# 新功能工作流

## 流程

### 1. 阅读设计文档

通过 CLAUDE.md 场景锚点找到对应设计文档，理解功能需求和约束。

### 2. 编写测试（推荐 TDD）

先写失败测试，定义预期行为：

```java
@Test
void newFeature_validInput_expectedResult() {
    // Given
    // When
    // Then
}
```

### 3. 实现

从底层向上编写代码：
1. 领域模型（model/）
2. 命令处理器（handler/）
3. 注册命令（Game.register()）
4. 渲染逻辑（ResponseRenderer，如需要）

### 4. 验证

- `mvn test` 全部通过
- 新功能测试覆盖正常流程 + 错误情况
- 检查文件行数（单文件 <= 400，单方法 <= 60）
- 检查依赖方向（无反向引用）

### 5. 文档更新

- 更新 `docs/reference/command-spec.md`
- 更新 `docs/plans/backlog.md`（标记完成）
- 如有新错误码，更新 `docs/reference/game-error-codes.md`
