# 原创测试投影

这三个文件由本项目的 `scripts/generate-test-fixtures.py` 生成，采用项目 MIT 许可。
它们描述同一座 7×5×6 小房子，包含 110 个非空气方块；使用 1.20 已存在的方块，覆盖玻璃、楼梯、含水属性、水和箱子。

- `original-house.litematic`：Litematica 位打包状态，包含跨 64 位边界的数据。
- `original-house.schem`：Sponge v3 palette / varint 数据。
- `original-house.nbt`：原版结构的 palette / block 列表。

三种格式必须在每个目标游戏版本中读取出完全相同的方块状态，并遵守格数上限。
这些是原创的微型测试数据，未包含玩家投影、存档或第三方建筑。
