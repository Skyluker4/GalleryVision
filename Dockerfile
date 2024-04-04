FROM debian:bookworm-20240211-slim

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    git \
    make \
    wget \
    python3 \
    unzip \
    adb \
    curl \
    ca-certificates \
    cmake \
    openjdk-17-jdk-headless \
  && rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*

WORKDIR /tmp
RUN curl -O https://dl.google.com/android/repository/android-ndk-r25c-linux.zip && cd /opt && unzip /tmp/android-ndk-r25c-linux.zip && rm -rf /tmp/*

RUN echo "export NDK_ROOT=/opt/android-ndk-r25c" >> ~/.bashrc

SHELL ["/bin/bash", "-c"]
RUN sed -i 's/-g/#-g/' /opt/android-ndk-r25c/build/cmake/android.toolchain.cmake
RUN sed -i 's/-g/#-g/' /opt/android-ndk-r25c/build/cmake/android-legacy.toolchain.cmake

COPY build.sh /build.sh
RUN chmod +x /build.sh
WORKDIR /paddle
CMD ["/build.sh"]

