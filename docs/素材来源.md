# 素材来源与授权

| 素材 | 来源 | 授权 | 用在哪里 |
|---|---|---|---|
| 落地钟、挂钟、座钟、八音盒、留声机、怀表、天文钟、台灯、保险柜的 3D 模型 | 先用火山方舟 Seedream 5.0 生成参考图（提示词见 `../../assets_lab/clockshop/prompts.tsv`），再用 Doubao-Seed3D 2.0 转成 3D，由 `../../tools/blender/prep_clockshop.sh`（`prep_ai.py`）减面 | AI 生成，本项目自有 | 四个房间里的道具 |
| 墙纸贴图、五张老照片、齿轮草图、铺子全景图 | Seedream 5.0 生成 | AI 生成，本项目自有 | 墙纸、相框里的照片、台灯下的草图；全景图用 `tools/ibl_source.py` + Filament cmgen 算成白天、深夜、旧日三档环境光 |
| 第五张照片 | 生成图里的招牌出现了真实钟表行的字号，已裁掉招牌，只留人物和钟 | 同上 | 阁楼相框 |
| 师傅的信、展示柜玻璃上的字 | 用 Noto Serif CJK（SIL Open Font License）由 ffmpeg 渲染成图片 | 字体 OFL；图片本项目自有 | 柜台上的信、午后阳光下玻璃上的字 |
| 房间（墙、地板、柜台、工作台、门、相框、天文钟的三个环、齿轮、钥匙、发条小鸟） | 代码里用基本几何体搭建（`render/RoomView.kt`） | 本项目自有 | 四个房间 |
| 钟声、八音盒 3-1-5 小曲、发条小鸟、滴答声 | `tools/make_sfx.py` 程序合成 | 本项目自有 | 拨对时刻、八音盒、通关、环境声 |
| 其他音效（指针、转盘、开盖、开门、拿起、翻纸、放下、选中、错误、找到零件） | Kenney「RPG Audio」「Interface Sounds」「Impact Sounds」（kenney.nl） | CC0 | 用 ffmpeg 转单声道，峰值统一到 -1 dB |
| 应用图标 | 怀表参考图用 `tools/cutout.py` 抠图，`tools/make_icon2d.py` 合成在墙纸圆盘上（源图在 `../../assets_lab/clockshop/icon/`） | 本项目自有 | 图标 |
| 背景音乐《Forget Me Not》（Kistol） | OpenGameArt（opengameart.org/content/forget-me-not） | CC0 | 循环背景音乐，响度统一到 -20 LUFS；另 2 首 CC0 备选在 `docs/音乐候选/`：《Clocktower / Time Passing Loop》（kiluaboy）、《Music Box: Cute Tune》（AureolusOmicron） |

钟表铺、老钟表匠和照片里的人物都是虚构的。钟表图鉴里的小知识是真实的历史。
