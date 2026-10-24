#include <jni.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include "arcore_c_api.h"
#include "gharmap/geometry.h"
#include <cmath>
#include <fstream>
#include <iomanip>
#include <locale>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

namespace {
std::mutex lock;
struct Capture {
    ArSession* session=nullptr; ArFrame* frame=nullptr;
    GLuint texture=0,program=0;
    int rotation=0,width=1,height=1; bool depth=false,recording=false;
    bool tracking=false; int64_t timestamp=0,lastLogged=0;
    std::vector<gharmap::Point> corners;
    std::vector<gharmap::Wall> walls;
    std::ofstream trace;
    std::string error;
    ~Capture(){if(session){if(recording)ArSession_stopRecording(session);ArSession_pause(session);}
        if(frame)ArFrame_destroy(frame);if(session)ArSession_destroy(session);}
};
Capture* ptr(jlong p){return reinterpret_cast<Capture*>(p);}
jstring text(JNIEnv* e,const std::string& s){return e->NewStringUTF(s.c_str());}
std::string str(JNIEnv* e,jstring s){if(!s)return {};const char* p=e->GetStringUTFChars(s,nullptr);std::string r(p);e->ReleaseStringUTFChars(s,p);return r;}
std::vector<gharmap::Point> points(JNIEnv* e,jdoubleArray a){
    std::vector<gharmap::Point> p;if(!a)return p;
    jsize n=e->GetArrayLength(a);if(n%2)return p;
    std::vector<double> d(n);e->GetDoubleArrayRegion(a,0,n,d.data());
    for(jsize i=0;i<n;i+=2)p.push_back({d[i],d[i+1]});return p;
}
GLuint shader(GLenum type,const char* source){
    GLuint s=glCreateShader(type);glShaderSource(s,1,&source,nullptr);glCompileShader(s);
    GLint ok=0;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);if(!ok){glDeleteShader(s);return 0;}return s;
}
void planes(Capture& c,float cameraX,float cameraZ){
    c.walls.clear();ArTrackableList* list=nullptr;ArTrackableList_create(c.session,&list);
    ArSession_getAllTrackables(c.session,AR_TRACKABLE_PLANE,list);int32_t count=0;
    ArTrackableList_getSize(c.session,list,&count);
    ArPose* pose=nullptr;ArPose_create(c.session,nullptr,&pose);
    for(int i=0;i<count;++i){
        ArTrackable* t=nullptr;ArTrackableList_acquireItem(c.session,list,i,&t);
        ArTrackingState state;ArTrackable_getTrackingState(c.session,t,&state);
        ArPlane* plane=ArAsPlane(t);ArPlaneType type;ArPlane_getType(c.session,plane,&type);
        ArPlane* parent=nullptr;ArPlane_acquireSubsumedBy(c.session,plane,&parent);
        if(parent){ArTrackable_release(ArAsTrackable(parent));ArTrackable_release(t);continue;}
        if(state!=AR_TRACKING_STATE_TRACKING||type!=AR_PLANE_VERTICAL){ArTrackable_release(t);continue;}
        float extent=0;ArPlane_getExtentX(c.session,plane,&extent);
        if(extent<0.4f){ArTrackable_release(t);continue;}
        ArPlane_getCenterPose(c.session,plane,pose);float m[16];ArPose_getMatrix(c.session,pose,m);
        double nx=m[4],ny=m[6],n=std::hypot(nx,ny);
        if(n>0.9){
            nx/=n;ny/=n;double d=nx*m[12]+ny*m[14];
            if(nx*cameraX+ny*cameraZ>d){nx=-nx;ny=-ny;d=-d;}
            double distance=d-nx*cameraX-ny*cameraZ;
            if(distance>0.15&&distance<8){
                bool dup=false;
                for(auto w:c.walls)if(nx*w.nx+ny*w.ny>0.995&&std::abs(d-w.distance)<0.12)dup=true;
                if(!dup)c.walls.push_back({nx,ny,d});
            }
        }
        ArTrackable_release(t);
    }
    ArPose_destroy(pose);ArTrackableList_destroy(list);
}
std::string stop(Capture& c){
    if(!c.recording)return {};
    auto status=ArSession_stopRecording(c.session);c.recording=false;c.trace.close();
    return status==AR_SUCCESS?"":"Recording could not be finalized; scan file may be incomplete";
}
}
extern "C" {
JNIEXPORT jlong JNICALL Java_com_gharmap_app_NativeBridge_create(JNIEnv*,jclass){return reinterpret_cast<jlong>(new Capture);}
JNIEXPORT void JNICALL Java_com_gharmap_app_NativeBridge_destroy(JNIEnv*,jclass,jlong h){std::lock_guard<std::mutex> g(lock);delete ptr(h);}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_resume(JNIEnv* e,jclass,jlong h,jobject activity){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);
    if(!c.session){
        auto result=ArSession_create(e,activity,&c.session);
        if(result!=AR_SUCCESS){c.session=nullptr;return text(e,"AR tracking unavailable on this device. Use measured-room entry instead.");}
        ArConfig* config=nullptr;ArConfig_create(c.session,&config);
        ArConfig_setPlaneFindingMode(c.session,config,AR_PLANE_FINDING_MODE_HORIZONTAL_AND_VERTICAL);
        ArConfig_setFocusMode(c.session,config,AR_FOCUS_MODE_AUTO);
        int32_t supported=0;ArSession_isDepthModeSupported(c.session,AR_DEPTH_MODE_AUTOMATIC,&supported);
        c.depth=supported!=0;
        if(c.depth)ArConfig_setDepthMode(c.session,config,AR_DEPTH_MODE_AUTOMATIC);
        auto configured=ArSession_configure(c.session,config);ArConfig_destroy(config);
        if(configured!=AR_SUCCESS){ArSession_destroy(c.session);c.session=nullptr;return text(e,"Could not configure camera tracking");}
        ArFrame_create(c.session,&c.frame);
    }
    ArSession_setDisplayGeometry(c.session,c.rotation,c.width,c.height);
    if(ArSession_resume(c.session)!=AR_SUCCESS)return text(e,"Camera unavailable. Close other camera apps and retry.");
    c.error.clear();return text(e,"");
}
JNIEXPORT void JNICALL Java_com_gharmap_app_NativeBridge_pause(JNIEnv*,jclass,jlong h){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);stop(c);c.tracking=false;
    if(c.session)ArSession_pause(c.session);
}
JNIEXPORT void JNICALL Java_com_gharmap_app_NativeBridge_surfaceCreated(JNIEnv*,jclass,jlong h){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);
    glGenTextures(1,&c.texture);glBindTexture(GL_TEXTURE_EXTERNAL_OES,c.texture);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    const char* v="attribute vec2 aPosition;attribute vec2 aUv;varying vec2 uv;void main(){gl_Position=vec4(aPosition,0.,1.);uv=aUv;}";
    const char* f="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES cameraTexture;varying vec2 uv;void main(){gl_FragColor=texture2D(cameraTexture,uv);}";
    GLuint vs=shader(GL_VERTEX_SHADER,v),fs=shader(GL_FRAGMENT_SHADER,f);
    if(!vs||!fs){if(vs)glDeleteShader(vs);if(fs)glDeleteShader(fs);c.error="Camera renderer unavailable";return;}
    c.program=glCreateProgram();glAttachShader(c.program,vs);glAttachShader(c.program,fs);glLinkProgram(c.program);
    glDeleteShader(vs);glDeleteShader(fs);GLint ok=0;glGetProgramiv(c.program,GL_LINK_STATUS,&ok);
    if(!ok){glDeleteProgram(c.program);c.program=0;c.error="Camera renderer unavailable";}
}
JNIEXPORT void JNICALL Java_com_gharmap_app_NativeBridge_resize(JNIEnv*,jclass,jlong h,jint rotation,jint w,jint height){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);c.rotation=rotation;c.width=w;c.height=height;
    glViewport(0,0,w,height);if(c.session)ArSession_setDisplayGeometry(c.session,rotation,w,height);
}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_draw(JNIEnv* e,jclass,jlong h){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);
    glClearColor(0.03f,0.08f,0.07f,1);glClear(GL_COLOR_BUFFER_BIT);
    c.tracking=false;
    if(c.session&&c.program){
        ArSession_setCameraTextureName(c.session,c.texture);
        auto updated=ArSession_update(c.session,c.frame);
        if(updated!=AR_SUCCESS)c.error="Camera update failed; pause and reopen the scan";
        else {
            ArFrame_getTimestamp(c.session,c.frame,&c.timestamp);
            const float vertices[]={-1,-1,1,-1,-1,1,1,1};float uv[8];
            ArFrame_transformCoordinates2d(c.session,c.frame,AR_COORDINATES_2D_OPENGL_NORMALIZED_DEVICE_COORDINATES,
                4,vertices,AR_COORDINATES_2D_TEXTURE_NORMALIZED,uv);
            if(c.timestamp>0){
                glDisable(GL_DEPTH_TEST);glUseProgram(c.program);glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_EXTERNAL_OES,c.texture);
                glUniform1i(glGetUniformLocation(c.program,"cameraTexture"),0);
                auto a=glGetAttribLocation(c.program,"aPosition"),b=glGetAttribLocation(c.program,"aUv");
                glVertexAttribPointer(a,2,GL_FLOAT,GL_FALSE,0,vertices);glVertexAttribPointer(b,2,GL_FLOAT,GL_FALSE,0,uv);
                glEnableVertexAttribArray(a);glEnableVertexAttribArray(b);glDrawArrays(GL_TRIANGLE_STRIP,0,4);
                glDisableVertexAttribArray(a);glDisableVertexAttribArray(b);
            }
            ArCamera* camera=nullptr;ArFrame_acquireCamera(c.session,c.frame,&camera);
            ArTrackingState state;ArCamera_getTrackingState(c.session,camera,&state);c.tracking=state==AR_TRACKING_STATE_TRACKING;
            if(c.tracking){
                ArPose* pose=nullptr;ArPose_create(c.session,nullptr,&pose);ArCamera_getPose(c.session,camera,pose);
                float m[16];ArPose_getMatrix(c.session,pose,m);ArPose_destroy(pose);
                if(c.timestamp-c.lastLogged>100000000){
                    planes(c,m[12],m[14]);c.lastLogged=c.timestamp;
                    if(c.trace.is_open()){
                        ArCameraIntrinsics* intrinsics=nullptr;ArCameraIntrinsics_create(c.session,&intrinsics);
                        ArCamera_getImageIntrinsics(c.session,camera,intrinsics);
                        float focal[2],principal[2];int32_t dims[2];
                        ArCameraIntrinsics_getFocalLength(c.session,intrinsics,&focal[0],&focal[1]);
                        ArCameraIntrinsics_getPrincipalPoint(c.session,intrinsics,&principal[0],&principal[1]);
                        ArCameraIntrinsics_getImageDimensions(c.session,intrinsics,&dims[0],&dims[1]);
                        ArCameraIntrinsics_destroy(intrinsics);
                        c.trace<<"{\"timestampNs\":"<<c.timestamp<<",\"cameraToWorldColumnMajor\":[";
                        for(int i=0;i<16;++i){if(i)c.trace<<",";c.trace<<m[i];}
                        c.trace<<"],\"intrinsics\":["<<focal[0]<<","<<focal[1]<<","<<principal[0]<<","<<principal[1]
                            <<"],\"imageSize\":["<<dims[0]<<","<<dims[1]<<"]}\n";
                    }
                }
            }
            ArCamera_release(camera);
        }
    }
    ArRecordingStatus recordingStatus=AR_RECORDING_NONE;
    if(c.session)ArSession_getRecordingStatus(c.session,&recordingStatus);
    if(c.recording&&recordingStatus==AR_RECORDING_IO_ERROR){stop(c);c.error="Recording stopped: storage error";}
    std::ostringstream s;s<<"{\"tracking\":"<<(c.tracking?"true":"false")<<",\"depth\":"<<(c.depth?"true":"false")
        <<",\"corners\":"<<c.corners.size()<<",\"walls\":"<<c.walls.size()<<",\"recording\":"<<(c.recording?"true":"false")
        <<",\"error\":\""<<c.error<<"\"}";
    return text(e,s.str());
}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_markCorner(JNIEnv* e,jclass,jlong h,jfloat x,jfloat y){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);
    if(!c.session||!c.tracking)return text(e,"Tracking is not ready. Move slowly and look at a textured floor.");
    ArHitResultList* list=nullptr;ArHitResultList_create(c.session,&list);ArFrame_hitTest(c.session,c.frame,x,y,list);
    int32_t count=0;ArHitResultList_getSize(c.session,list,&count);
    std::string result="No floor found here. Point at a visible floor corner and try again.";
    for(int i=0;i<count;++i){
        ArHitResult* hit=nullptr;ArHitResult_create(c.session,&hit);ArHitResultList_getItem(c.session,list,i,hit);
        ArTrackable* t=nullptr;ArHitResult_acquireTrackable(c.session,hit,&t);
        ArTrackableType type;ArTrackable_getType(c.session,t,&type);
        if(type==AR_TRACKABLE_PLANE){
            ArPlane* plane=ArAsPlane(t);ArPlaneType planeType;ArPlane_getType(c.session,plane,&planeType);
            if(planeType==AR_PLANE_HORIZONTAL_UPWARD_FACING){
                ArPose* pose=nullptr;ArPose_create(c.session,nullptr,&pose);ArHitResult_getHitPose(c.session,hit,pose);
                int32_t inside=0;ArPlane_isPoseInPolygon(c.session,plane,pose,&inside);
                float m[16];ArPose_getMatrix(c.session,pose,m);ArPose_destroy(pose);
                if(inside){c.corners.push_back({m[12],m[14]});result="Corner added";}
            }
        }
        ArTrackable_release(t);ArHitResult_destroy(hit);if(result=="Corner added")break;
    }
    ArHitResultList_destroy(list);return text(e,result);
}
JNIEXPORT void JNICALL Java_com_gharmap_app_NativeBridge_undo(JNIEnv*,jclass,jlong h){std::lock_guard<std::mutex> g(lock);auto& p=ptr(h)->corners;if(!p.empty())p.pop_back();}
JNIEXPORT void JNICALL Java_com_gharmap_app_NativeBridge_clearCorners(JNIEnv*,jclass,jlong h){std::lock_guard<std::mutex> g(lock);ptr(h)->corners.clear();}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_autoOutline(JNIEnv* e,jclass,jlong h){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);
    if(!c.tracking)return text(e,"Tracking is not ready");
    auto outline=gharmap::intersectWalls(c.walls);
    if(outline.empty())return text(e,"A closed room was not detected. Scan every wall or mark floor corners.");
    c.corners=outline;return text(e,"Experimental convex outline. Check every wall before saving.");
}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_polygon(JNIEnv* e,jclass,jlong h){
    std::lock_guard<std::mutex> g(lock);return text(e,gharmap::polygonJson(ptr(h)->corners));
}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_startRecording(JNIEnv* e,jclass,jlong h,jstring path){
    std::lock_guard<std::mutex> g(lock);auto& c=*ptr(h);
    if(!c.session||!c.tracking)return text(e,"Wait for tracking before recording");
    if(c.recording)return text(e,"");
    std::string file=str(e,path);ArRecordingConfig* config=nullptr;ArRecordingConfig_create(c.session,&config);
    std::string uri="file://"+file;ArRecordingConfig_setMp4DatasetUri(c.session,config,uri.c_str());
    ArRecordingConfig_setAutoStopOnPause(c.session,config,1);
    auto status=ArSession_startRecording(c.session,config);ArRecordingConfig_destroy(config);
    if(status!=AR_SUCCESS)return text(e,"Could not start recording. Check available storage.");
    c.recording=true;c.trace.open(file+".jsonl",std::ios::out|std::ios::trunc);
    if(!c.trace){stop(c);return text(e,"Could not write scan metadata");}
    c.trace.imbue(std::locale::classic());c.trace<<std::setprecision(9);
    c.trace<<"{\"schemaVersion\":1,\"provider\":\"ARCore\",\"units\":\"metres\",\"coordinates\":\"right-handed-y-up\"}\n";
    return text(e,"");
}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_stopRecording(JNIEnv* e,jclass,jlong h){std::lock_guard<std::mutex> g(lock);return text(e,stop(*ptr(h)));}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_validate(JNIEnv* e,jclass,jdoubleArray a){
    auto p=points(e,a);auto v=gharmap::validate(p);return text(e,v.valid?"":v.reason);
}
JNIEXPORT jstring JNICALL Java_com_gharmap_app_NativeBridge_exportSvg(JNIEnv* e,jclass,jdoubleArray a,jstring label){
    return text(e,gharmap::svg(points(e,a),str(e,label)));
}
}
