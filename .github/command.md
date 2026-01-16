 
 让我更直接地查看 RemoteControlService 的日志
 adb -s 0005214AL000902 shell "am force-stop com.phoneagent.remote" && sleep 2 && adb -s 0005214AL000902 shell "am start -n com.phoneagent.remote/.ui.MainActivity" && sleep 10 && adb -s 0005214AL000902 logcat -d -v time | grep "RemoteControlService\|FrpManager" | tail -60

 docker compose logs phoneagent --tail 100 | grep -E "Docker environment|localhost:6104" | tail -20
 docker compose exec phoneagent adb devices

 容器内没有ADB设备。需要让容器连接到宿主机的ADB隧道：

 docker compose exec phoneagent adb connect host.docker.internal:6104 && docker compose exec phoneagent adb devices