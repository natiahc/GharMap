#import "GeometryBridge.h"
#include "gharmap/geometry.h"
namespace {
std::vector<gharmap::Point> unpack(NSArray<NSNumber*>* xy) {
    std::vector<gharmap::Point> p;if(xy.count%2)return p;
    for(NSUInteger i=0;i<xy.count;i+=2)p.push_back({xy[i].doubleValue,xy[i+1].doubleValue});return p;
}
}
@implementation GMGeometry
+ (NSString*)validate:(NSArray<NSNumber*>*)xy {
    auto result=gharmap::validate(unpack(xy));return result.valid?@"":[NSString stringWithUTF8String:result.reason.c_str()];
}
+ (double)area:(NSArray<NSNumber*>*)xy {return gharmap::validate(unpack(xy)).area;}
+ (NSString*)svg:(NSArray<NSNumber*>*)xy label:(NSString*)label {
    auto s=gharmap::svg(unpack(xy),label.UTF8String);return [NSString stringWithUTF8String:s.c_str()];
}
+ (NSArray<NSNumber*>*)outline:(NSArray<NSNumber*>*)walls {
    if(walls.count%3)return @[];
    std::vector<gharmap::Wall> w;for(NSUInteger i=0;i<walls.count;i+=3)w.push_back({walls[i].doubleValue,walls[i+1].doubleValue,walls[i+2].doubleValue});
    auto p=gharmap::intersectWalls(w);NSMutableArray<NSNumber*>* result=[NSMutableArray new];
    for(auto v:p){[result addObject:@(v.x)];[result addObject:@(v.y)];}return result;
}
@end
