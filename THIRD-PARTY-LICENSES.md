# 第三方许可与核查记录

核查日期：2026-10-18。范围为本项目实际随附字体、当前 Gradle 脚本直接声明的依赖和构建工具。本清单不是完整传递依赖 SBOM，也不构成对所有素材权属的确认。上游下载原文未改写；URL、SHA-256 见 [下载记录](licenses/download-provenance.json)、[依赖来源记录](licenses/dependency-provenance.json)及[补充依赖来源记录](licenses/additional-dependency-provenance.json)。

## 随附字体：实际 metadata

使用 bundled Python 检查发现未安装 fontTools，因此使用标准库 `struct` 直接读取实际 TTF 的 SFNT `name`、`head`、`fvar` 表，没有依据文件名猜版本。可复现的只读脚本为 [inspect-font-metadata.py](licenses/inspect-font-metadata.py)，完整输出为 [font-metadata.json](licenses/font-metadata.json)。

| 实际文件 | 内嵌版本（name ID 5） | 内嵌版权（name ID 0） | 许可与原文 |
| --- | --- | --- | --- |
| [inter.ttf](app/src/main/res/font/inter.ttf) | `Version 4.001;git-66647c0bb` | `Copyright 2016 The Inter Project Authors (https://github.com/rsms/inter)` | SIL OFL 1.1；[内嵌 git 提交对应上游原文](licenses/Inter-4.001-upstream-LICENSE.txt) |
| [noto_sans_sc.ttf](app/src/main/res/font/noto_sans_sc.ttf) | `Version 2.004-H2;hotconv 1.0.118;makeotfexe 2.5.65603` | `(c) 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.` | SIL OFL 1.1；[Google Fonts 官方原文](licenses/NotoSansSC-OFL.txt) |

两份字体的 name ID 13 均明确声明 SIL Open Font License 1.1。Inter 为可变字体，`wght=100..900`、默认 400，`opsz=14..32`；Noto Sans SC 为可变字体，`wght=100..900`、默认 100。后者 name ID 1 为 `Noto Sans SC Thin`，name ID 16 为 `Noto Sans SC`，不能据此把整个文件误写为仅 Thin 的静态字体。

字体 SHA-256：

- Inter（876576 bytes）：`29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`
- Noto Sans SC（17772300 bytes）：`a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`

官方来源：

- Inter：[rsms/inter 内嵌提交 66647c0bb 的 LICENSE.txt](https://raw.githubusercontent.com/rsms/inter/66647c0bb/LICENSE.txt)。该原文版权年份 2016 与字体 metadata 一致。另保留 [Google Fonts Inter OFL](licenses/Inter-OFL.txt)，来自 [google/fonts 官方仓库](https://raw.githubusercontent.com/google/fonts/main/ofl/inter/OFL.txt)，该文件首行年份为 2020。两份均原样保留，不擅自统一版权年份。
- Noto Sans SC：[google/fonts 官方 OFL.txt](https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/OFL.txt)，首行 Adobe 2014–2021 与 Reserved Font Name `Source` 与 metadata 对应。该下载地址为可变的 `main` 分支，保留的哈希固定了本次取得的许可内容；尚未将本地字体二进制与历史发行包逐字节匹配，不能称已确认其下载时间、来源提交或历史发布包。

OFL 原文与版权应随字体再分发；字体本身受 OFL 管理，不因打包进本项目而转为项目自有版权。有关修改、保留字体名称和再分发的具体条件以所附原文为准。

## 当前直接依赖与工具

声明证据：[app/build.gradle.kts](app/build.gradle.kts)、[根 build.gradle.kts](build.gradle.kts)、[wrapper properties](gradle/wrapper/gradle-wrapper.properties)。以下许可证根据对应版本的官方 Google Maven/Maven Central POM 核查，POM 原件均保存在本目录下的 licenses 中；来源 URL 和哈希可由上方 JSON 记录复核。

表中 Compose 版本为 [BOM 2024.09.03](licenses/compose-bom-2024.09.03.pom) 的约束值，不表示已运行 Gradle 确认每个变体最终解析结果。其余版本为脚本显式声明值。

| 组件 / Maven 坐标 | 声明版本或 BOM 约束 | 用途 | 核实的许可 / 本地证据 |
| --- | --- | --- | --- |
| `androidx.core:core-ktx` | 1.13.1 | 应用 | Apache-2.0；[POM](licenses/core-ktx-1.13.1.pom) |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.6 | 应用 | Apache-2.0；[POM](licenses/lifecycle-runtime-ktx-2.8.6.pom) |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.6 | 应用 | Apache-2.0；[POM](licenses/lifecycle-runtime-compose-2.8.6.pom) |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.6 | 应用 | Apache-2.0；[POM](licenses/lifecycle-viewmodel-compose-2.8.6.pom) |
| `androidx.activity:activity-compose` | 1.9.2 | 应用 | Apache-2.0；[POM](licenses/activity-compose-1.9.2.pom) |
| `androidx.compose:compose-bom` | 2024.09.03 | 版本约束 | Apache-2.0；[POM](licenses/compose-bom-2024.09.03.pom) |
| `androidx.compose.ui:ui` | 1.7.3（BOM） | 应用 | Apache-2.0；[POM](licenses/ui-1.7.3.pom) |
| `androidx.compose.ui:ui-graphics` | 1.7.3（BOM） | 应用 | Apache-2.0；[POM](licenses/ui-graphics-1.7.3.pom) |
| `androidx.compose.ui:ui-tooling-preview` | 1.7.3（BOM） | 应用 | Apache-2.0；[POM](licenses/ui-tooling-preview-1.7.3.pom) |
| `androidx.compose.material3:material3` | 1.3.0（BOM） | 应用 | Apache-2.0；[POM](licenses/material3-1.3.0.pom) |
| `androidx.compose.material:material-icons-extended` | 1.7.3（BOM） | 应用图标库 | Apache-2.0；[POM](licenses/material-icons-extended-1.7.3.pom) |
| `androidx.room:room-runtime` | 2.6.1 | 应用 | Apache-2.0；[POM](licenses/room-runtime-2.6.1.pom) |
| `androidx.room:room-ktx` | 2.6.1 | 应用 | Apache-2.0；[POM](licenses/room-ktx-2.6.1.pom) |
| `androidx.room:room-compiler` | 2.6.1 | KSP 编译期 | POM 同时列 Apache-2.0 和 BSD licence（链接 ANTLR）；[POM](licenses/room-compiler-2.6.1.pom)。不将其简化为仅 Apache，也不推定两项可任意择一 |
| `androidx.exifinterface:exifinterface` | 1.3.7 | 应用 | Apache-2.0；[POM](licenses/exifinterface-1.3.7.pom) |
| `androidx.compose.ui:ui-tooling` | 1.7.3（BOM） | debugImplementation | Apache-2.0；[POM](licenses/ui-tooling-1.7.3.pom) |
| `androidx.compose.ui:ui-test-manifest` | 1.7.3（BOM） | debugImplementation | Apache-2.0；[POM](licenses/ui-test-manifest-1.7.3.pom) |
| `junit:junit` | 4.13.2 | 本地单元测试 | **EPL-1.0**；[POM](licenses/junit-4.13.2.pom)、[该版本完整许可](licenses/JUnit-4.13.2-LICENSE.txt) |
| `org.json:json` | 20240303 | 本地单元测试，替代 Android 桩 | **Public Domain**；[POM](licenses/json-20240303.pom)、[该版本上游声明](licenses/JSON-java-20240303-LICENSE.txt)。不能套用旧版 JSON License 或误标 Apache-2.0 |
| `com.android.application` / `com.android.tools.build:gradle` | 8.5.2 | Android Gradle Plugin | Apache-2.0；[POM](licenses/agp-8.5.2.pom) |
| `org.jetbrains.kotlin.android` / Kotlin Gradle plugin | 2.0.21 | 构建插件 | Apache-2.0；[POM](licenses/kotlin-gradle-plugin-2.0.21.pom) |
| `org.jetbrains.kotlin.plugin.compose` | 2.0.21 | Compose compiler 插件 | Apache-2.0；[POM](licenses/compose-compiler-gradle-plugin-2.0.21.pom) |
| `com.google.devtools.ksp` | 2.0.21-1.0.28 | 构建插件 | Apache-2.0；[POM](licenses/ksp-2.0.21-1.0.28.pom) |
| Gradle wrapper / Gradle | 8.7 | 构建启动器、构建工具 | Gradle 主体 Apache-2.0；[8.7 官方完整 LICENSE](licenses/Gradle-8.7-LICENSE.txt) 还包含第三方例外，不能理解为整个发行包只有 Apache |

补充已识别的测试传递依赖：JUnit 4.13.2 POM 引入 `org.hamcrest:hamcrest-core:1.3`；其 [POM](licenses/hamcrest-core-1.3.pom) 继承 [hamcrest-parent:1.3](licenses/hamcrest-parent-1.3.pom) 的 New BSD License。未声称已穷尽所有传递依赖。

Gradle wrapper 的 [POSIX 脚本](gradlew) 和 [Windows 脚本](gradlew.bat) 均自带 Apache-2.0 声明。实际 [wrapper JAR](gradle/wrapper/gradle-wrapper.jar) 的 SHA-256 为 `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`，与 [Gradle 官方 8.7 wrapper 校验值](https://services.gradle.org/distributions/gradle-8.7-wrapper.jar.sha256) 一致，结果见 [wrapper-verification.json](licenses/wrapper-verification.json)。版本确认因此不仅依赖 distributionUrl。

## 未核实项与范围限制

- **TravelPlanAPP 权属未核实。** 未取得其作者、仓库许可、授权链或与本团队关系的证据；本清单不宣称 TravelPlanAPP 属于本队或已获授权，其代码、设计和素材的使用权仍需单独证实。
- **图像生成工具许可未核实。** 未确认实际工具/模型、账号方案、生成时适用条款、输入素材授权及输出使用条件；不能以“AI 生成”直接推导为原创、无版权或可商用，也没有在此给出工具许可结论。
- 字体内嵌名称、版本与 OFL 声明已读取；本地字体和历史上游发行包的二进制同一性未核实。Inter 内嵌提交的许可证已取得；Noto 对应历史二进制来源仍待追溯。
- 未执行构建或解析全部 release/debug/test 变体依赖，避免向许可范围之外写入构建缓存。本清单没有确认最终 APK 内的全部代码、传递库、NOTICE 或许可文件是否已经打包；根目录许可文件不会自动进入 APK。
- Room compiler POM 的 BSD 项已保留，具体内嵌 ANTLR 代码版本、版权通知与再分发要求未逐项审计；Gradle 发行包、Android SDK、JDK 及所有构建工具传递依赖也未做完整组件审计。
- 上述本地单元测试依赖按 Gradle 声明属于测试范围，不据此声称它们存在于最终 APK。若再分发完整源码/构建工具，应保留相应第三方声明。
