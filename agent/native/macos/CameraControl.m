#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

static NSString *authorization(void) {
    switch ([AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo]) {
        case AVAuthorizationStatusAuthorized: return @"authorized";
        case AVAuthorizationStatusDenied: return @"denied";
        case AVAuthorizationStatusRestricted: return @"restricted";
        default: return @"notDetermined";
    }
}
static void requireAuthorization(void) {
    if ([authorization() isEqual:@"authorized"]) return;
    if ([authorization() isEqual:@"notDetermined"]) {
        dispatch_semaphore_t completed = dispatch_semaphore_create(0);
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
            dispatch_semaphore_signal(completed);
        }];
        NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:75];
        while (dispatch_semaphore_wait(completed, DISPATCH_TIME_NOW) != 0) {
            if ([deadline timeIntervalSinceNow] <= 0) [NSException raise:@"Permission" format:@"Confirme o acesso à câmera no Mac e tente novamente."];
            [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.05]];
        }
    }
    if (![authorization() isEqual:@"authorized"]) {
        if ([authorization() isEqual:@"denied"]) {
            NSTask *settings = [NSTask new];
            settings.executableURL = [NSURL fileURLWithPath:@"/usr/bin/open"];
            settings.arguments = @[@"x-apple.systempreferences:com.apple.preference.security?Privacy_Camera"];
            [settings launchAndReturnError:nil];
        }
        [NSException raise:@"Permission" format:@"Acesso à câmera não autorizado. Em Ajustes → Privacidade e Segurança → Câmera, autorize Control Desk Camera e reabra o agente se solicitado."];
    }
}
static NSString *modeName(NSInteger mode) {
    return mode == 0 ? @"locked" : mode == 1 ? @"auto" : @"continuous";
}
static NSArray<AVCaptureDevice *> *cameras(void) {
    // Includes cameras exposed by legacy drivers; no AVCaptureSession or device input is created.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    return [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
#pragma clang diagnostic pop
}
static NSDictionary *describe(AVCaptureDevice *device) {
    NSMutableArray *focus = [NSMutableArray array], *exposure = [NSMutableArray array], *balance = [NSMutableArray array];
    for (NSInteger mode = 0; mode <= 2; mode++) {
        if ([device isFocusModeSupported:(AVCaptureFocusMode)mode]) [focus addObject:@(mode)];
        if ([device isExposureModeSupported:(AVCaptureExposureMode)mode]) [exposure addObject:@(mode)];
        if ([device isWhiteBalanceModeSupported:(AVCaptureWhiteBalanceMode)mode]) [balance addObject:@(mode)];
    }
    NSMutableArray *actions = [NSMutableArray array];
    NSArray *names = @[@"LOCKED", @"AUTO", @"CONTINUOUS"];
    // A fixed-only property offers no actual configuration choice.
    if (focus.count > 1) for (NSNumber *mode in focus) [actions addObject:[@"FOCUS_" stringByAppendingString:names[mode.integerValue]]];
    if (exposure.count > 1) for (NSNumber *mode in exposure) [actions addObject:[@"EXPOSURE_" stringByAppendingString:names[mode.integerValue]]];
    if (balance.count > 1) for (NSNumber *mode in balance) [actions addObject:[@"WHITE_BALANCE_" stringByAppendingString:names[mode.integerValue]]];
    NSString *auth = authorization();
    if (![auth isEqual:@"authorized"] && ![auth isEqual:@"restricted"]) [actions addObject:@"REQUEST_CAMERA_ACCESS"];
    return @{@"id": device.uniqueID, @"name": device.localizedName, @"capabilities": actions,
             @"settings": @{@"authorization": auth, @"focusMode": modeName(device.focusMode),
                             @"exposureMode": modeName(device.exposureMode), @"whiteBalanceMode": modeName(device.whiteBalanceMode)}};
}
static void execute(AVCaptureDevice *device, NSString *action) {
    if ([action isEqual:@"REQUEST_CAMERA_ACCESS"]) { requireAuthorization(); return; }
    requireAuthorization();
    // Validate the capability again immediately before acquiring the configuration lock.
    if (![[describe(device) objectForKey:@"capabilities"] containsObject:action])
        [NSException raise:@"Unsupported" format:@"A câmera não suporta esta ação nativa."];
    NSError *error = nil;
    if (![device lockForConfiguration:&error]) [NSException raise:@"Configuration" format:@"Não foi possível configurar esta câmera: %@", error.localizedDescription];
    @try {
        NSInteger mode = [action hasSuffix:@"LOCKED"] ? 0 : [action hasSuffix:@"AUTO"] ? 1 : 2;
        if ([action hasPrefix:@"FOCUS_"]) device.focusMode = (AVCaptureFocusMode)mode;
        else if ([action hasPrefix:@"EXPOSURE_"]) device.exposureMode = (AVCaptureExposureMode)mode;
        else if ([action hasPrefix:@"WHITE_BALANCE_"]) device.whiteBalanceMode = (AVCaptureWhiteBalanceMode)mode;
        else [NSException raise:@"Unsupported" format:@"Ação desconhecida."];
    } @finally { [device unlockForConfiguration]; }
}
int main(int argc, const char *argv[]) {
    @autoreleasepool {
        @try {
            if (argc > 1 && strcmp(argv[1], "execute") == 0) {
                if (argc != 4) [NSException raise:@"Arguments" format:@"Informe dispositivo e ação."];
                NSString *uid = [NSString stringWithUTF8String:argv[2]], *action = [NSString stringWithUTF8String:argv[3]];
                AVCaptureDevice *selected = nil;
                for (AVCaptureDevice *device in cameras()) if ([device.uniqueID isEqual:uid]) selected = device;
                if (!selected) [NSException raise:@"Missing" format:@"A câmera selecionada não está mais disponível."];
                execute(selected, action);
            } else if (argc != 2 || strcmp(argv[1], "discover") != 0) {
                [NSException raise:@"Arguments" format:@"Operação desconhecida."];
            }
            NSMutableArray *rows = [NSMutableArray array];
            for (AVCaptureDevice *device in cameras()) [rows addObject:describe(device)];
            NSData *json = [NSJSONSerialization dataWithJSONObject:rows options:0 error:nil];
            fwrite(json.bytes, 1, json.length, stdout);
            return 0;
        } @catch (NSException *exception) {
            fprintf(stderr, "%s\n", exception.reason.UTF8String);
            return 1;
        }
    }
}
