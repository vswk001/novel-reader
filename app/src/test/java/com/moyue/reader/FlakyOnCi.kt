package com.moyue.reader

/**
 * 标记【在 CI 上不阻塞构建】的测试。
 *
 * 用于 Robolectric + Compose 全链路测试（createAndroidComposeRule）：这类测试会等待界面空闲，
 * 而在 CI 的慢 runner 上存在已知的测试框架时序缺陷，可能一直等到超时并抛
 * AppNotIdleException —— 与被测业务逻辑无关（本机与正常负载下稳定通过）。
 *
 * 这些测试仍然会在 CI 上【运行并报告结果】，只是失败不再阻断流水线；
 * 在本地它们与其它测试一样正常参与并阻塞。
 *
 * 判定方式：CI 的测试任务按「类上是否带此注解」分组，见 .github/workflows。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class FlakyOnCi
