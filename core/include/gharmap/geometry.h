#pragma once
#include <string>
#include <vector>

namespace gharmap {
// Metres on a floor: x = AR world x; y = AR world z. No assumed geographic north.
struct Point { double x, y; };
struct Validation { bool valid; std::string reason; double area; };
// Inward half-plane: nx*x + ny*y <= distance. Normal need not be unit length.
struct Wall { double nx, ny, distance; };
Validation validate(const std::vector<Point>& points);
double perimeter(const std::vector<Point>& points);
// Returns no polygon for unbounded, inconsistent or insufficient observations.
// Convex-room hypothesis only: callers must label output experimental/unverified.
std::vector<Point> intersectWalls(const std::vector<Wall>& walls);
std::string polygonJson(const std::vector<Point>& points);
std::string svg(const std::vector<Point>& points, const std::string& label);
}
