# Bug 修复工作流

## 流程

### 1. 复现

编写一个失败的测试来复现 bug：

```java
@Test
void harvestCommand_witheredCrop_returnsCorrectError() {
    // 模拟枯萎状态
    session.setPlot(1, witheredCrop);
    CommandResult result = harvestCommand.execute(session, new String[]{"1"});

    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("枯萎"));
}
```

### 2. 定位

通过 `docs/architecture/data-flow.md` 追踪消息流，定位问题所在层：
- 解析层（CommandParser）
- 调度层（GameEngine）
- 命令处理层（Command Handler）
- 渲染层（ResponseRenderer）
- 会话层（SessionManager）

### 3. 修复

最小改动修复 bug：
- 只修改问题所在的代码
- 不重构相邻代码
- 不添加"顺便"的改进

### 4. 验证

- 复现测试现在通过
- 原有测试全部通过（无回归）
- `mvn test` 通过

### 5. 文档

- 如果 bug 涉及未记录的边界情况，更新 `command-spec.md`
- 如果添加了新的错误消息，更新 `game-error-codes.md`
