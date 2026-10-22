# Heaven Aura (Forge 1.20.1) — сабмод для Timeless Power

Поки Made in Heaven (timelesspower:made_in_heaven) лежить у руці, носій і всі неворожі істоти в радіусі 50 блоків
отримують Силу III, Стійкість IV і Регенерацію II. Вороги (клас Enemy: зомбі, SCP-моби, розбійники...) не отримують нічого.
Ціна: носій поступово втрачає їжу (holderExhaustionPerSecond).

Налаштування: config/heaven_aura.json (предмети, радіус, рівні ефектів, extraEffects, ціна).
Збірка: GitHub Actions -> build -> Artifacts. Потрібні Forge 47.x і Timeless Power.

## v1.1
Виправлено мигання ефектів, коли Made in Heaven запущено: Hourglass тоді прискорює спливання тривалості ефектів
(команда `hourglass config potionEffect ALWAYS`). Тепер ефекти безкінечні і знімаються самим модом, коли артефакт
прибрали, істота вийшла з радіусу, носій помер/вийшов або сервер зупиняється.
