## 20250331：最近在研究它的代码。

安卓设备端运行的是 server，其代码入口在“server.java”。

编译server，参考 <https://github.com/Genymobile/scrcpy/blob/master/doc/build.md>


```
# Linux
export ANDROID_SDK_ROOT=~/Android/Sdk

meson setup x --buildtype=release --strip -Db_lto=true
ninja -Cx  # DO NOT RUN AS ROOT

# 或者到 release路径下 

./build_server.sh

# 编译结果在： /release/work/build-server/server/scrcpy-server

```




运行测试：

```
adb push scrcpy-server /data/local/tmp/scrcpy-server.jar
adb forward tcp:27183 localabstract:scrcpy
adb shell CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 2.1

```





---

## adb导入server并且使用VLC正向连接读取流实验。

```
# 到scrcpy工具路径下

adb push scrcpy-server /data/local/tmp/scrcpy-server-manual.jar

# 正向建立一个adb通道，本机12349端口就桥接到安卓本地套接字了
adb forward tcp:12349 localabstract:scrcpy

# 下面命令 tunnel_forward=true 使server等候连接
adb shell CLASSPATH=/data/local/tmp/scrcpy-server-manual.jar app_process / com.genymobile.scrcpy.Server 2.1 tunnel_forward=true audio=false control=false cleanup=false raw_stream=true max_size=1920

# VLC 路径下，可以打开视频流
PS C:\Program Files (x86)\VideoLAN\VLC> .\vlc.exe -Idummy --demux=h264 --network-caching=0 tcp://localhost:12349

```
参考 <https://github.com/Genymobile/scrcpy/blob/master/doc/develop.md>

