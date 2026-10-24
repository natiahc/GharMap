#include "gharmap/geometry.h"
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace gharmap;
void require(bool ok,const char* message){if(!ok)throw std::runtime_error(message);}
int main(){
    std::vector<Point> room{{0,0},{4,0},{4,3},{0,3}};
    require(validate(room).valid,"rectangle");
    require(std::abs(validate(room).area-12)<1e-8,"area in square metres");
    require(std::abs(perimeter(room)-14)<1e-8,"perimeter");
    require(!validate({{0,0},{4,3},{0,3},{4,0}}).valid,"crossed walls rejected");
    require(!validate({{0,0},{4,0},{4,0},{0,3}}).valid,"duplicates rejected");
    require(!validate({{0,0},{1,0},{2,0}}).valid,"collinear rejected");
    require(!validate({{0,0},{1,0},{0,std::numeric_limits<double>::quiet_NaN()}}).valid,"NaN rejected");
    require(validate({{0,0},{3,0},{3,1},{1,1},{1,3},{0,3}}).valid,"concave assisted polygon");
    auto p=intersectWalls({{-1,0,0},{1,0,4},{0,-1,0},{0,1,3}});
    require(p.size()==4&&std::abs(validate(p).area-12)<1e-8,"wall intersection");
    require(intersectWalls({{-1,0,0},{1,0,4},{0,-1,0}}).empty(),"open room must not invent wall");
    require(intersectWalls({{-1,0,-5},{1,0,4},{0,-1,0},{0,1,3}}).empty(),"inconsistent walls");
    require(intersectWalls({{0,0,3},{1,0,4},{0,1,3}}).empty(),"bad normals");
    auto exportSvg=svg(room,"Room <&> \"A\"");
    require(exportSvg.find("&lt;&amp;&gt; &quot;A&quot;")!=std::string::npos,"XML escaped");
    require(exportSvg.find("12.00 m²")!=std::string::npos,"export dimensions");
    require(svg(room,"部屋 🏠").find("部屋 🏠")!=std::string::npos,"international UTF-8 labels");
    require(svg(room,std::string("Room")+char(1)).find(char(1))==std::string::npos,"XML control characters removed");
    require(svg({},"invalid").empty(),"no invalid SVG");
    require(polygonJson(room)=="[[0,0],[4,0],[4,3],[0,3]]","portable serialization");
    std::cout<<"All geometry tests passed\n";
}
