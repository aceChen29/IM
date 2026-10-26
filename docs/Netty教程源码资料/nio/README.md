# NIO 练习

这是普通的 Java 17 Demo，只使用 JDK 自带的 NIO API，不需要 Maven、Gradle、Spring Boot 或 Netty。

在 IDEA 的项目视图中右键 `nio` 目录，选择 **Mark Directory as → Sources Root（标记目录为 → 源代码根目录）**，并确认项目使用 JDK 17。这样 IDEA 才会把这里的 `.java` 文件作为 Java 源码分析，提供类型检查和方法补全。输入 `ssc.conf` 后，可以按 `Ctrl+Space` 手动触发补全；正确的方法名是 `configureBlocking(false)`。

如果运行时提示 `The output path is not specified for module IM`，打开 **File → Project Structure → Modules → IM → Paths**，选择 **Inherit project compile output path**，然后应用设置。本项目的 **Project compiler output** 已设置为 `IM/out`。这是 IDEA 编译输出目录的设置，不需要增加 Maven 或 Gradle 构建文件。

这里的 `NioServer.java` 是用于边写边练习的代码，运行前需要补全服务端逻辑。

也可以在本目录用命令行运行：先执行 `javac NioServer.java NioClient.java`，在两个终端分别执行 `java NioServer` 和 `java NioClient`。

客户端暂时使用阻塞式连接，便于先专注学习服务端事件循环。
