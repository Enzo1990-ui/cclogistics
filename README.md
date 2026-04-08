# ⚙️ Create: Colony Logistics

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green)
![Modloader](https://img.shields.io/badge/NeoForge-1.21.1-orange)
![Dependencies](https://img.shields.io/badge/Requires-Create_%7C_MineColonies-blue)

**Create: Colony Logistics** is a Minecraft integration mod that seamlessly bridges the gap between the robust mechanical logistics of **Create** and the dynamic, citizen-driven supply chains of **MineColonies**. 

Have you ever built a massive, fully automated Create factory, only to realize your MineColonies citizens still need items hand-delivered? Have you ever wanted your colony's excess cobblestone to be automatically whisked away into your global item network? This mod is your solution!

---

## 📦 Core Features

* **The Freight Depot:** The heart of your new supply chain. This custom MineColonies building acts as a two-way bridge between your colony's request system and a Create **Stock Ticker**. 
* **The Foremen's Hut:** The administrative center for your logistics network. From this building, you can monitor live shipping manifests and manage your specialized logistics workers.
* **Logistics Controller:** A dedicated routing computer. Use its custom UI to map exactly which Create network addresses correspond to your colony, allowing for pinpoint accuracy when importing and exporting goods.
* **Smart "Customs" Protection:** Features a built-in 30-minute protection timer that prevents newly imported items from being immediately flagged as "excess" and shipped back out.

---

## 👷 Meet Your New Citizens

Your colony gains three brand-new, fully animated professions dedicated entirely to keeping your supply lines moving:

1. **The Logistics Coordinator:** Constantly audits your main Warehouse. When they detect excess inventory (over 128 items), they generate internal pickup requests to clear the clutter.
2. **The Packer Agent:** Stationed at the Freight Depot, the Packer takes the excess items gathered from your colony, physically packs them into **Create Cardboard Packages**, and ships them off to your global Create network. They also unpack incoming shipments!
3. **The Freight Inspector:** The paper-pusher of the operation. The Inspector physically travels between the Freight Depot and the Foremen's Hut, collecting incoming and outgoing shipping logs so you can monitor your imports and exports in real-time.

*(Note: Custom worker skins are fully supported!)*

---

## 🔧 How It Works (The Logistics Loop)

To use the mod it really is quite simple

- Build the train depot.

- Open up the freight depot gui click logistics and choose a name for colony and create

- Link your freight depot hut with your stock ticker

- In your create storage have a packager hooked up with a stock link that feeds into a train. 

- Have a packager hooked up to your create system and a belt feeding into that with a package filter named your create name exporting items from the train.

- Hire your logistics coordinator and packager agents.

- Let them work.
---

## 📥 Installation & Dependencies

This mod requires the following to function properly:
* [NeoForge](https://neoforged.net/) (1.21.1)
* [Create](https://modrinth.com/mod/create)
* [MineColonies](https://modrinth.com/mod/minecolonies)

---

## 💻 For Developers

**Setup Workspace:**

1. **Clone the repository:**
   git clone https://github.com/ogtenzohd/CreateColonyLogistics.git

2. **Setup the NeoForge workspace:**
   ./gradlew genIntellijRuns (or genEclipseRuns)

3. **Build the mod:**
   ./gradlew build

---

## 📝 License & Credits

* Mod created by **ogtenzohd**.
* Built using the NeoForge API.
* Deeply thankful to the developers of **Create** and **MineColonies** for their incredible APIs and modding frameworks.

**Stop carrying stacks of materials by hand. Automate your colony today!**
