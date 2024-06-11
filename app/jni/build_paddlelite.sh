#!/bin/sh

set -e

export PATH="/opt/Android/cmake/3.22.1/bin:$PATH"
LITE_BUILD_THREADS=$(nproc) || 1
export LITE_BUILD_THREADS
export NDK_ROOT="/opt/Android/ndk/25.2.9519653"
export NDK_NAME="android-ndk-r25c"

# Debug
#SHARED_FLAGS="--android_stl=c++_shared --with_extra=ON --with_cv=ON --with_log=ON --with_exception=ON --with_nnadapter=ON --nnadapter_with_android_nnapi=ON --android_api_level=33"
#./Paddle-Lite/lite/tools/build_android.sh  --with_arm82_fp16=ON --with_arm8_sve2=ON "${SHARED_FLAGS}"
#./Paddle-Lite/lite/tools/build_android.sh --arch=armv7 --toolchain=clang "${SHARED_FLAGS}"

# Release
SHARED_FLAGS="--android_stl=c++_shared --with_extra=ON --with_cv=ON --with_log=OFF --with_exception=OFF --with_nnadapter=ON --nnadapter_with_android_nnapi=ON --android_api_level=33"
./Paddle-Lite/lite/tools/build_android.sh  --with_arm82_fp16=ON --with_arm8_sve2=ON "${SHARED_FLAGS}"
./Paddle-Lite/lite/tools/build_android.sh --arch=armv7 "${SHARED_FLAGS}"
