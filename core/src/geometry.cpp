#include "gharmap/geometry.h"
#include <algorithm>
#include <cmath>
#include <iomanip>
#include <locale>
#include <sstream>

namespace gharmap {
namespace {
constexpr double eps = 1e-7;
double cross(Point a, Point b, Point c) {
    return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
}
double length(Point a, Point b) { return std::hypot(a.x-b.x,a.y-b.y); }
bool on(Point a, Point b, Point p) {
    return std::abs(cross(a,b,p))<eps && p.x>=std::min(a.x,b.x)-eps &&
        p.x<=std::max(a.x,b.x)+eps && p.y>=std::min(a.y,b.y)-eps &&
        p.y<=std::max(a.y,b.y)+eps;
}
bool intersects(Point a, Point b, Point c, Point d) {
    double p=cross(a,b,c),q=cross(a,b,d),r=cross(c,d,a),s=cross(c,d,b);
    if (((p>eps&&q< -eps)||(p< -eps&&q>eps)) &&
        ((r>eps&&s< -eps)||(r< -eps&&s>eps))) return true;
    return on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b);
}
std::string escape(const std::string& s) {
    std::string out;
    for(char c:s) switch(c) {
        case '&':out+="&amp;";break; case '<':out+="&lt;";break;
        case '>':out+="&gt;";break; case '"':out+="&quot;";break;
        case '\'':out+="&apos;";break; default:out+=c;
    }
    return out;
}
std::ostringstream stream() { std::ostringstream s; s.imbue(std::locale::classic()); return s; }
}
Validation validate(const std::vector<Point>& p) {
    if(p.size()<3) return {false,"Mark at least three distinct corners",0};
    double twice=0;
    for(size_t i=0;i<p.size();++i) {
        if(!std::isfinite(p[i].x)||!std::isfinite(p[i].y)) return {false,"Non-finite coordinate",0};
        if(std::abs(p[i].x)>10000||std::abs(p[i].y)>10000) return {false,"Coordinate outside supported range",0};
        for(size_t j=0;j<i;++j) if(length(p[i],p[j])<0.02) return {false,"Duplicate corners",0};
        Point a=p[i],b=p[(i+1)%p.size()];
        if(length(a,b)<0.05) return {false,"Wall shorter than five centimetres",0};
        if(std::abs(cross(p[(i+p.size()-1)%p.size()],a,b))<eps)
            return {false,"Remove collinear corners",0};
        twice+=a.x*b.y-a.y*b.x;
    }
    for(size_t i=0;i<p.size();++i) for(size_t j=i+1;j<p.size();++j) {
        if(j==i+1||(i==0&&j==p.size()-1)) continue;
        if(intersects(p[i],p[(i+1)%p.size()],p[j],p[(j+1)%p.size()]))
            return {false,"Walls cross; undo and mark corners in perimeter order",0};
    }
    double area=std::abs(twice)*0.5;
    if(area<0.25) return {false,"Room area below 0.25 square metres",0};
    return {true,"Unverified geometry: confirm measurements on site",area};
}
double perimeter(const std::vector<Point>& p) {
    double result=0; for(size_t i=0;i<p.size();++i) result+=length(p[i],p[(i+1)%p.size()]); return result;
}
std::vector<Point> intersectWalls(const std::vector<Wall>& input) {
    if(input.size()<3) return {};
    std::vector<Wall> walls;
    for(auto w:input) {
        double n=std::hypot(w.nx,w.ny);
        if(!std::isfinite(n)||!std::isfinite(w.distance)||n<eps) return {};
        walls.push_back({w.nx/n,w.ny/n,w.distance/n});
    }
    // If normals leave a semicircle uncovered, the feasible region is unbounded.
    std::vector<double> angles;
    for(auto w:walls) angles.push_back(std::atan2(w.ny,w.nx));
    std::sort(angles.begin(),angles.end());
    constexpr double pi=3.14159265358979323846;
    double gap=angles.front()+2*pi-angles.back();
    for(size_t i=1;i<angles.size();++i) gap=std::max(gap,angles[i]-angles[i-1]);
    if(gap>=pi-eps) return {};
    std::vector<Point> vertices;
    for(size_t i=0;i<walls.size();++i) for(size_t j=i+1;j<walls.size();++j) {
        auto a=walls[i],b=walls[j]; double det=a.nx*b.ny-a.ny*b.nx;
        if(std::abs(det)<eps) continue;
        Point p{(a.distance*b.ny-a.ny*b.distance)/det,(a.nx*b.distance-a.distance*b.nx)/det};
        bool inside=true;
        for(auto w:walls) if(w.nx*p.x+w.ny*p.y>w.distance+eps) {inside=false;break;}
        if(!inside) continue;
        bool duplicate=false;for(auto v:vertices) if(length(v,p)<1e-5) duplicate=true;
        if(!duplicate) vertices.push_back(p);
    }
    if(vertices.size()<3) return {};
    Point center{0,0};for(auto p:vertices){center.x+=p.x;center.y+=p.y;}
    center.x/=vertices.size();center.y/=vertices.size();
    std::sort(vertices.begin(),vertices.end(),[&](Point a,Point b){
        return std::atan2(a.y-center.y,a.x-center.x)<std::atan2(b.y-center.y,b.x-center.x);
    });
    return validate(vertices).valid ? vertices : std::vector<Point>{};
}
std::string polygonJson(const std::vector<Point>& points) {
    auto s=stream();s<<std::setprecision(12)<<"[";
    for(size_t i=0;i<points.size();++i){if(i)s<<",";s<<"["<<points[i].x<<","<<points[i].y<<"]";}
    s<<"]";return s.str();
}
std::string svg(const std::vector<Point>& p,const std::string& label) {
    auto check=validate(p);if(!check.valid) return {};
    double minx=p[0].x,maxx=minx,miny=p[0].y,maxy=miny;
    for(auto v:p){minx=std::min(minx,v.x);maxx=std::max(maxx,v.x);miny=std::min(miny,v.y);maxy=std::max(maxy,v.y);}
    double scale=std::min(620/(maxx-minx),660/(maxy-miny));
    auto x=[&](Point v){return 90+(v.x-minx)*scale;};
    auto y=[&](Point v){return 160+(v.y-miny)*scale;};
    auto s=stream();s<<std::fixed<<std::setprecision(2);
    s<<"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"1000\" viewBox=\"0 0 800 1000\">"
       "<rect width=\"800\" height=\"1000\" fill=\"white\"/><g font-family=\"sans-serif\" fill=\"#153a36\">"
       "<text x=\"48\" y=\"52\" font-size=\"28\">GharMap</text><text x=\"48\" y=\"92\" font-size=\"20\">"
      <<escape(label)<<"</text><polygon fill=\"#e6f3ee\" stroke=\"#153a36\" stroke-width=\"4\" points=\"";
    for(auto v:p)s<<x(v)<<","<<y(v)<<" ";
    s<<"\"/>";
    for(size_t i=0;i<p.size();++i){auto a=p[i],b=p[(i+1)%p.size()];
        s<<"<text x=\""<<(x(a)+x(b))/2<<"\" y=\""<<(y(a)+y(b))/2-9
         <<"\" text-anchor=\"middle\" font-size=\"14\">"<<length(a,b)<<" m</text>";}
    s<<"<text x=\"48\" y=\"900\" font-size=\"18\">Area: "<<check.area<<" m²</text>"
       "<text x=\"48\" y=\"936\" font-size=\"14\">Unverified scan / user measurements. Not a construction drawing.</text>"
       "<text x=\"48\" y=\"960\" font-size=\"14\">Orientation is local; geographic north has not been established.</text></g></svg>";
    return s.str();
}
}
