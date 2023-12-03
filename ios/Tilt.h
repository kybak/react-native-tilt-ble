
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNTiltSpec.h"

@interface Tilt : NSObject <NativeTiltSpec>
#else
#import <React/RCTBridgeModule.h>

@interface Tilt : NSObject <RCTBridgeModule>
#endif

@end
