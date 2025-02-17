**Hephaestus** ist unser Custom-Item-System. Es erlaubt uns, neue Items zu erstellen und Vanilla-Items zu überschreiben. 

Hephaestus nutzt dabei Minecrafts `Data Component`-System. Das macht die Konfiguration etwas komplexer, erlaubt uns aber auch, sehr einfach sämtliche Eigenschaften eines Items zu verändern - denn jedes Vanilla-Item ist auch nur eine Kombination der selben `Data Components`.

Für eine Übersicht der Data Components, empfiehlt sich das [Minecraft-Wiki](https://minecraft.wiki/w/Data_component_format). Besonders ist zu beachten, das einzelne Components exklusiv sind. 
Beispielsweise sind `damage` und `max_stack_size` nicht kombinierbar - Items mit Schaden können nicht gestackt werden.

## Neues Item erstellen
Ein neues Item wird in-game erstellt. Nehme dazu ein existierenden Item in die Hand, und nutze `/he register <key>`.
Das Item wird dabei zum Basis-Item, und kann dann in der Konfiguration weiter angepasst werden.

Der `<key>` ist ein sogenannter Namespaced-Key im Format `<namespace>:<name>`. Als Namespace nutzen wir für Items `erethon`.
Der `<name>` ist der Name des Items, und muss eindeutig sein. Desweiteren sollte er nur Kleinbuchstaben und Unterstriche enthalten - keine Sonderzeichen oder Leerzeichen.
Bitte halte den Namen auch in **Englisch**.

Wenn ich also beispielsweise "Theanor-Schlamm" erstellen möchte, nehme ich das Basis-Item in die Hand und gebe `/he register erethon:theanor_mud` ein.

Dies erstellt eine Konfigurationsdatei im Ordner `hephaestus/items/erethon/theanor_mud.yml`. Diese Datei kann dann bearbeitet werden um das Item zu verändern.

## Item-Konfiguration
Die Item-Konfiguration nutzt die bereits erwähnten `Data Components`. Diese sind in der Konfiguration als JSON-Strings angegeben. JSON ist dabei sehr empfindlich gegenüber Syntax-Fehlern, also bitte darauf achten, dass die Konfiguration korrekt ist.
Es gibt Online JSON-Validatoren, die helfen können, Syntaxfehler zu finden.

Die Data Components sind dabei in zwei Abschnitte unterteilt: `vanilla` und `patch`. `vanilla` enthält die Vanilla-Data Components des Items, und `patch` enthält die Änderungen, die an dem Item vorgenommen werden sollen.
Wichtig: `vanilla` ist *nie* aktiv. Es dient nur als Referenz, wird automatisch bei Minecraft-Updates aktualisiert und sollte nicht verändert werden. Alle Änderungen müssen im `patch`-Abschnitt vorgenommen werden. 
Wenn etwas in `vanilla` geändert werden soll, kopiere den entsprechenden Abschnitt in `patch` und ändere ihn dort.

Es gibt einige Dinge, die nicht im `patch` konfiguriert werden sollten:
* `item_name`: Wird automatisch generiert
* `lore`: Wird automatisch generiert
* `custom_data`: Wird automatisch generiert

Es gibt einige weitere Dinge, die sich direkt in der Konfiguration einstellen lassen:
* `key`: Der Namespaced-Key des Items. Wird automatisch generiert und sollte nicht verändert werden.
* `baseItem`: Der Namespaced-Key des Basis-Items. Dieses Item wird überschrieben. Das ist immer ein Vanilla-Item.
* `name`: Der Name des Items. Hier können mehrere Sprachen angegeben werden. Optional.
* `flavour`: Der Flavour-Text des Items. Dieser erscheint in grau und kursiv unter dem Namen. Optional.
* `random`: Konfiguration für zufällige Items. Hier kann eingestellt werden, wie das Item generiert wird. Optional.

Diese Einstellungen sollten immer im obersten Abschnitt der Konfiguration vorgenommen werden und nicht im `patch`.

Alle anderen Einstellungen sollten im `patch`-Abschnitt vorgenommen werden.

## Beispiel-Konfiguration
Hier ein Beispiel für eine Item-Konfiguration. Das Item...
1) basiert auf einem Diamantschwert 
2) hat eine eigene ID (`erethon:speedy_sword`)
3) hat einen eigenen Namen und Flavour-Text
4) einen `vanilla`-Abschnitt, der die Vanilla-Eigenschaften des normalen Diamantschwertes enthält
5) einen `patch`-Abschnitt, der die Eigenschaften des Items verändert:
   1) erhöht die Bewegungsgeschwindigkeit um 10. Die `id` hier muss einzigartig sein, aber nicht zwingend dem Item entsprechen.
   2) `item_name` und `custom_data`, die wir beide ignorieren, da sie oben konfiguriert werden.
   3) `DataVersion`, das wir ebenfalls ignorieren. Dies wird nur für Minecraft-Update benötigt.
6) hat eine zufällige Level- und Seltenheitsverteilung
7) hat eine zufällige Anzahl an Slots

Generell kann es empfehlenswert sein, sich die Vanilla-Eigenschaften eines anderen Items anzusehen, das ähnlich zu dem ist, was man erstellen möchte. Das erleichtert die Konfiguration.

```yaml
key: erethon:speedy_sword
# The base item this custom item is based on. If this is a vanilla item, this will be the vanilla item.
baseItem: minecraft:diamond_sword
name:
  de: "Schnellschwert"
  en: "Speedy Sword"
flavour:
  de: "Ein Schwert, das so schnell ist, dass man dauernd gegen Wände rennt."
  en: "A sword so fast, you'll keep running into walls."  
# JSON representation of the item's vanilla default data components.
# This is never applied. Copy/paste values to the patch below to modify the item's properties.
vanilla: |-
  {
    "minecraft:item_model": "minecraft:diamond_sword",
    "minecraft:item_name": "{\"translate\":\"item.minecraft.diamond_sword\"}",
    "minecraft:enchantable": {
      "value": 10
    },
    "minecraft:attribute_modifiers": {
      "modifiers": [
        {
          "type": "minecraft:attack_damage",
          "id": "minecraft:base_attack_damage",
          "amount": 6.0,
          "operation": "add_value",
          "slot": "mainhand"
        },
        {
          "type": "minecraft:attack_speed",
          "id": "minecraft:base_attack_speed",
          "amount": -2.4000000953674316,
          "operation": "add_value",
          "slot": "mainhand"
        }
      ]
    },
    "minecraft:repair_cost": 0,
    "minecraft:tool": {
      "rules": [
        {
          "blocks": "minecraft:cobweb",
          "speed": 15.0,
          "correct_for_drops": true
        },
        {
          "blocks": "#minecraft:sword_efficient",
          "speed": 1.5
        }
      ],
      "damage_per_block": 2
    },
    "minecraft:lore": [],
    "minecraft:damage": 0,
    "minecraft:rarity": "common",
    "minecraft:repairable": {
      "items": "#minecraft:diamond_tool_materials"
    },
    "minecraft:enchantments": {
      "levels": {}
    },
    "minecraft:max_stack_size": 1,
    "minecraft:max_damage": 1561,
    "DataVersion": 4189
  }
# Our patch, as JSON. This is used to actually modify the item
# See https://minecraft.wiki/w/Data_component_format for all possible components.
# Components here will override vanilla components.
patch: |-
  {
    "minecraft:custom_data": {
      "hephaestus-id": "erethon:test"
    },
    "minecraft:item_name": {
      "translate": "hephaestus.item.speedy_sword.name"
    }
    "minecraft:attribute_modifiers": {
      "modifiers": [
        {
          "type": "minecraft:movement_speed",
          "id": "erethon:speed_sword",
          "amount": 10,
          "operation": "add_value",
          "slot": "mainhand"
        }
        ]
    },
    "DataVersion": 4189
  }
random:
  # Level weights, the higher the weight, the more likely the level is to be chosen.
  level:
    - min: 1      # Levels 1-10 are very common (weight 100)
      max: 10
      weight: 100
    - min: 11     # Levels 11-20 are less common (weight 50)
      max: 20
      weight: 50
  rarity:
    1: # At level 1
      common: 80 # Common items are 80% likely
      rare: 20
    2: # At level 2
      common: 70 # This decreases to 70%
      rare: 30
  # Slot weights: determines the max orb upgrades available 
  slots:
    "1": # Slot amount weights for level 1 (determines max upgrades)
      - min: 0
        max: 0
        weight: 90 # At level 1, it's very likely to have no slots
      - min: 1
        max: 1
        weight: 10 # But there is a small chance to have 1 slot
    "2": # Slot weights for level 2
      - min: 1
        max: 2
        weight: 90 # At level 2, it's very likely to have 1-2 slots
      - min: 3
        max: 3
        weight: 10 # But there is a small chance to have 3 slots
```