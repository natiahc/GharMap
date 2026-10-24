#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
@interface GMGeometry : NSObject
+ (NSString *)validate:(NSArray<NSNumber *> *)xy;
+ (double)area:(NSArray<NSNumber *> *)xy;
+ (NSString *)svg:(NSArray<NSNumber *> *)xy label:(NSString *)label;
+ (NSArray<NSNumber *> *)outline:(NSArray<NSNumber *> *)walls;
@end
NS_ASSUME_NONNULL_END
