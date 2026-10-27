# Минимальный комплект UI PBR — 2026-10-25

`[x]` — выполнено; `[ ]` — ещё не проверено. Этот этап сокращает ассеты для
существующего контракта материалов. BC-сжатие и новая GPU-инфраструктура в него не входят.

## Фактические потребители

- [x] `UiPbrTextureSets.directory()` адресует все шесть наборов.
- [x] `UiPassCompiler` и `OrderedUiBatcher` подключают их по запросу материала.
- [x] `ui_pbr_surface.glsl` использует normal XY, roughness, height, color и metallic.
- [x] `ui_ice_material.glsl` использует normal XY, roughness, height, color и AO;
  один ICE-набор обслуживает surface и liquid glass.

Отсутствие материала на текущем экране не означает отсутствия потребителя.
Удаление любого из 12 runtime-файлов сейчас приводит к ошибке при включении
соответствующего материала: автоматического fallback у загрузчика нет.
Поэтому оставлены две упакованные карты на материал и удалены лишние верхние mip levels.

## Решения по картам

Все исходники — 2048×2048. Числа VRAM ниже — точный объём несжатых texel data со всей
mip chain, без выравнивания и служебной памяти драйвера; фактическая GPU allocation не измерена.
`CM` = `color_metal.ktx`, `CA` = `color_ao.ktx`, `NRH` = `normal_rough_height.ktx`.
Для каждой строки fallback сейчас отсутствует: missing asset вызывает ошибку.

| Material | Map / вклад | Source size / precision | Runtime size | GPU format | VRAM, bytes | Decision |
|---|---|---|---|---|---:|---|
| Graphite | CM: тёмный цвет покрытия, metallic | 2048² / RGB8, constant M=1 | 256² | RGBA8_UNORM | 349 524 | Downscale / Pack |
| Graphite | NRH: порошковый рельеф и roughness | 2048² / N16, R8, H16 | 256² | RGBA16_UNORM | 699 048 | Downscale / Pack |
| Soft-touch | CM: цвет полимера, M=0 | 2048² / palette RGB8 | 256² | RGBA8_UNORM | 349 524 | Downscale / Pack |
| Soft-touch | NRH: поры, царапины, roughness | 2048² / N16, R8, H16 | 256² | RGBA16_UNORM | 699 048 | Downscale / Pack |
| Satin Titanium | CM: оттенок металла, M=1 | 2048² / RGB8, constant M=1 | 256² | RGBA8_UNORM | 349 524 | Downscale / Pack |
| Satin Titanium | NRH: слабый рельеф, разброс roughness | 2048² / N16, R8, H16 | 256² | RGBA16_UNORM | 699 048 | Downscale / Pack |
| Brushed Metal | CM: неоднородный цвет и редкие неметаллические дефекты | 2048² / RGB8, M8 | 256² | RGBA8_UNORM | 349 524 | Downscale / Pack |
| Brushed Metal | NRH: царапины, roughness, height | 2048² / N16, R8, H16 | 256² | RGBA16_UNORM | 699 048 | Downscale / Pack |
| Ceramic | CM: почти белый цвет, M=0 | 2048² / palette RGB8 | 256² | RGBA8_UNORM | 349 524 | Downscale / Pack |
| Ceramic | NRH: слабые нормали, неоднородность roughness/height | 2048² / N16, R8, H16 | 256² | RGBA16_UNORM | 699 048 | Downscale / Pack |
| ICE | CA: цвет пропускания и затемнение трещин | 2048² / RGB8, AO8 | 512² | RGBA8_UNORM | 1 398 100 | Downscale / Pack |
| ICE | NRH: преломление, блики, микрорельеф | 2048² / N16, R8, H16 | 512² | RGBA16_UNORM | 2 796 200 | Downscale / Pack |

Стандартный tileSize у пяти твёрдых материалов — 160–256 физических пикселей;
256² покрывает этот диапазон без обязательных 2K-карт. Для ICE оставлен запас 512²
из-за оптического вклада нормалей и height. Это начальный компактный профиль, а не
доказательство визуальной эквивалентности при любом масштабе: API допускает tileSize до 1024.

Сохранены 16 бит для normal/height. Перенос roughness в отдельную 8-битную карту,
удаление почти постоянного цвета Ceramic или объединение microtiles потребуют изменения
bindings/шейдеров и отдельной визуальной проверки. Constant metallic уже находится
в alpha вместе с цветом и отдельной runtime-текстуры не занимает. Graphite подавляет
metallic параметром материала, несмотря на белую исходную маску.

## Удалённые исходники

- [x] Удалены пять `*_NormalDX.png` и `ice/ice_0002_normal_directx_2k.png`.
  Подготовщик читает только OpenGL-нормали. Освобождено **113 852 157 bytes (108,58 MiB)**.
- [x] Оставлены 23 ambientCG-мастера и пять ICE-мастеров вне runtime/JAR.
  Маленькие исходные metallic-маски сохранены для воспроизводимой упаковки.
- [x] Обновлён `SOURCE_AUDIT.md`: размеры, precision, статистика и SHA-256 оставшихся исходников.
  Provenance, ссылки источников и лицензии остаются в `README.md` и third-party notices.

## Измерения

| Показатель | До | После |
|---|---:|---:|
| Runtime KTX files | 12 | 12 |
| KTX bytes на диске | 402 654 840 (384,00 MiB) | 9 438 704 (9,00 MiB) |
| Texel payload всех наборов | 402 653 160 | 9 437 160 |
| Один обычный набор, texel payload | 67 108 860 | 1 048 572 |
| ICE, texel payload | 67 108 860 | 4 194 300 |
| Сумма zlib DEFLATE level 6 для 12 KTX | 272 167 498 | 6 861 872 |

KTX уменьшились на **97,66%**. DEFLATE здесь — отдельное сравнение одних и тех же
ресурсов, а не измерение размера полного JAR. Не измерены frame time, upload time,
texture bandwidth и фактическое выделение памяти драйвером.

## Воспроизводимость и проверки

- [x] `PrepareUiPbr.java` повторно подготовил все 12 KTX из сохранённых 2K-мастеров.
  Цвет усредняется в linear light; нормали усредняются в XYZ, затем нормализуются;
  потерянная variance учитывается в roughness. Height/metallic/AO остаются linear.
- [x] Все **110 сохранённых mip levels** побайтно совпали (SHA-256) с соответствующими
  уровнями прежних 2K KTX. Нового квантования или изменения упаковки каналов нет.
- [x] Загрузчик читает размер и mip count из KTX; ограничение — квадратные power-of-two
  карты до 2048² с полной цепочкой до 1². RGBA8/RGBA16 и два sampler bindings сохранены.
- [x] Gradle `compileJava`, `compileTestJava`; все тесты `*UiMaterial*` и `*UiIce*`.
  Проверены KTX всех шести наборов, malformed dimensions/mip count, truncated data,
  precision и shader compilation для OpenGL/Vulkan (не runtime-тест Vulkan).
- [ ] Полностью успешный расширенный запуск с `*UiGlass*`: **18/19 прошли**.
  `UiGlassShaderCompileTest` запрашивает отсутствующий `ui_glass_frozen.frag`;
  список этого теста и GLSL-файлы в данном этапе не менялись. Отчёт запуска:
  `build/reports/tests/test/index.html`.
- [ ] Сравнение Minecraft captures для штатных/увеличенных tiles, ICE refraction и height lighting.

Height shader использует шаг `1 / textureSize(..., 0)`. После удаления верхних уровней
этот шаг меняется; совпадение самих mip data не доказывает идентичность height lighting.
Физические GLSL-модели на этом этапе не изменены. Визуальная приёмка и дальнейший выбор
общих microtiles остаются открытыми; согласование с Agent A не проводилось.

Команда воспроизведения:

```powershell
java -Xmx2G tools/materials/PrepareUiPbr.java assets-source/ui-pbr src/main/resources/assets/combatant/textures/ui/pbr tools/materials
.\gradlew.bat compileJava test --tests '*UiMaterial*' --tests '*UiIce*' --tests '*UiGlass*'
```

## Изменённые файлы этого этапа

- `src/main/resources/assets/combatant/textures/ui/pbr/{graphite,soft_touch,satin_titanium,brushed_metal,ceramic}/`
  — уменьшены `color_metal.ktx` и `normal_rough_height.ktx` в каждом наборе.
- `src/main/resources/assets/combatant/textures/ui/pbr/ice/`
  — уменьшены `color_ao.ktx` и `normal_rough_height.ktx`.
- `tools/materials/PrepareUiPbr.java` — воспроизводит компактные размеры и полные mip chains.
- `src/main/java/combatant/client/render/engine/renderer/ui/UiMaterialTextureData.java`
  — проверяет и предоставляет размеры из KTX header.
- `src/main/java/combatant/client/render/engine/renderer/ui/UiPbrTextureSets.java`
  — создаёт и загружает текстуры по размерам файла вместо фиксированного 2048².
- `src/test/java/combatant/client/render/engine/shader/UiMaterialShaderTest.java`
  — проверяет компактные ассеты и отклонение недопустимых KTX layouts.
- `assets-source/ui-pbr/README.md`, `tools/materials/README.md`, `docs/ui_material/b.md`
  и этот аудит — актуальные размеры, решения и отметки выполнения.
- `tools/materials/SOURCE_AUDIT.md`, `tools/materials/packed-preview.png`
  — локальные отчёт и preview перегенерированы; шесть удалённых исходников перечислены выше.

`docs/`, исходные изображения и генерируемые preview/source audit уже исключены из Git
локальными ignore-правилами; отметки в `b.md` сохранены на диске, правила не менялись.
