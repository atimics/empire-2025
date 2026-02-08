(ns empire.tutorial.scenarios)

;; Each scenario returns a map with:
;;   :id          - keyword identifier
;;   :name        - display name
;;   :description - one-line description for the menu
;;   :map-strings - vector of ASCII row strings (see test-utils/char->cell)
;;   :pages       - vector of objective text strings

(defn scenario-movement []
  {:id :movement
   :name "Movement & Terrain"
   :description "Learn to move your army and explore terrain"
   :map-strings
   ["~~~~~~~~~~~~"
    "~~####~~~~~~"
    "~######~~~~~"
    "~###O##~~~~~"
    "~####A#~~~~~"
    "~######~~~~~"
    "~~####~~~~~~"
    "~~~##~~~~~~~"
    "~~~~~~~~~~~~"
    "~~~~~~~~~~~~"]
   :pages
   ["Welcome to Empire! You command armies, ships,\nand aircraft to conquer the world.\n\nLet's start with the basics: moving your army."
    "Your army is the 'A' on the map. Move it\nwith the QWEASDZXC keys:\n\n  Q W E      (diagonal and cardinal\n  A   D       directions around\n  Z X C       your unit)"
    "Terrain types:\n  Green = your cities\n  Brown = land\n  Blue  = sea\n  Dark  = unexplored (fog of war)\n\nArmies can only move on land and into cities."
    "Try moving your army around the island to\nexplore! As you move, fog of war clears.\n\nPress SPACE to skip a unit's turn.\nPress P to pause/unpause the game.\n\nPress [N] for next page, [ESC] to hide overlay."]})

(defn scenario-production []
  {:id :production
   :name "City Production"
   :description "Produce military units from your cities"
   :map-strings
   ["~~~~~~~~~~~~~~~"
    "~~~####~~~~~~~~"
    "~~######~~~~~~~"
    "~~##O###~~~~~~~"
    "~~######~~~~~~~"
    "~~~####~~~~~~~~"
    "~~~~##~~~~~~~~~"
    "~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~"]
   :pages
   ["Cities are your factories. When a city needs\norders, it will flash for your attention.\n\nYou choose what to produce by pressing a key."
    "Production keys:\n  A = Army      (5 rounds)\n  F = Fighter   (10 rounds)\n  D = Destroyer (20 rounds)\n  T = Transport (30 rounds)\n  P = Patrol Boat (15 rounds)\n  S = Submarine (20 rounds)\n  C = Carrier   (30 rounds)\n  B = Battleship (40 rounds)"
    "Only coastal cities can build naval units.\nThe progress bar in a city shows how close\nthe unit is to completion.\n\nPress SPACE to skip a city's production turn.\nPress X to cancel production."
    "Start by producing an army from your city.\nOnce built, the army will appear and await\nyour orders.\n\nExperiment with different unit types!"]})

(defn scenario-combat []
  {:id :combat
   :name "Combat & Conquest"
   :description "Attack and capture enemy cities"
   :map-strings
   ["~~~~~~~~~~~~~~~"
    "~~~#####~~~~~~~"
    "~~#######~~~~~~"
    "~~###O###~~~~~~"
    "~~##A####~~~~~~"
    "~~####+##~~~~~~"
    "~~#####+#~~~~~~"
    "~~~#####~~~~~~~"
    "~~~~###~~~~~~~~"
    "~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~"]
   :pages
   ["Conquering cities is how you win Empire.\nFree cities (white) can be captured by\nmoving an army into them."
    "To capture a city, move your army adjacent\nto it, then move INTO the city cell.\n\nEach capture attempt has a 50% chance of\nsuccess. If it fails, your army is destroyed!"
    "There are two free cities (+) south of your\narrow. Move your army to capture them.\n\nOnce captured, cities turn green and start\nproducing units for you."
    "Tips:\n- Build multiple armies for safer conquest\n- Fighters can fly over enemy cities\n- Ships cannot enter undamaged cities\n\nCapture both free cities to complete\nthis scenario!"]})

(defn scenario-naval []
  {:id :naval
   :name "Naval & Transports"
   :description "Build ships and transport armies overseas"
   :map-strings
   ["~~~~~~~~~~~~~~~~~~~~"
    "~~####~~~~~~~~~~~~~~"
    "~######~~~~~~~~~~~~~"
    "~##O###~~~~~~~~~~~~~"
    "~###T##~~~~~~~~~~~~~"
    "~######~~~~~~~~~~~~~"
    "~~####~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~####~~~"
    "~~~~~~~~~~~~##+##~~~"
    "~~~~~~~~~~~~#####~~~"
    "~~~~~~~~~~~~~###~~~~"]
   :pages
   ["Armies can't swim! To cross water, you need\na Transport ship (T).\n\nYou have a Transport in the harbor near\nyour city."
    "Loading armies onto a transport:\n1. Move an army adjacent to the transport\n2. Move the army INTO the transport's cell\n3. The army boards automatically\n\nTransports hold up to 6 armies."
    "Moving a loaded transport:\n- Transports move like armies but on water\n- Use QWEASDZXC to move\n- Press U to unload (wake) armies aboard\n\nTo disembark: when aboard, move toward land."
    "Your mission:\n1. Build an army at your city\n2. Load it onto the Transport\n3. Sail south-east to the distant island\n4. Disembark and capture the free city (+)\n\nGood luck, Admiral!"]})

(defn scenario-fighters []
  {:id :fighters
   :name "Fighters & Fuel"
   :description "Master fighter aircraft and fuel management"
   :map-strings
   ["~~~~~~~~~~~~~~~~~~~~"
    "~~####~~~~~~~~~~~~~~"
    "~######~~~~~~~~~~~~~"
    "~##OF##~~~~~~~~~~~~~"
    "~######~~~~~~~~~~~~~"
    "~~####~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~###~~~"
    "~~~~~~~~~~~~~#+##~~~"
    "~~~~~~~~~~~~~~###~~~"]
   :pages
   ["Fighters (F) are fast and powerful, but they\nhave limited fuel. A fighter has 20 fuel and\nuses 1 per move.\n\nWhen fuel runs out, the fighter crashes!"
    "Refueling:\n- Land on any friendly city to refuel\n- Land on a Carrier at sea to refuel\n- Fighters refuel fully when they land\n\nPlan your routes carefully!"
    "Your fighter is at your city, fully fueled.\nThe target island is to the south-east.\n\nFighters can fly over water and enemy units.\nThey move 4 times per round (speed 4)."
    "Mission:\n- Fly your fighter to scout the distant island\n- Make sure you can return before fuel runs out!\n- The round trip is about 18 cells\n\nWatch your fuel carefully."]})

(defn scenario-carriers []
  {:id :carriers
   :name "Carriers & Air Power"
   :description "Operate carriers to project air power"
   :map-strings
   ["~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~####~~~~~~~~~~~~~~~~~~~"
    "~######~~~~~~~~~~~~~~~~~~"
    "~##O###~~~~~~~~~~~~~~~~~~"
    "~######~~~~~~~~~~~~~~~~~~"
    "~~####~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~C~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~###~~"
    "~~~~~~~~~~~~~~~~~~~#+##~~"
    "~~~~~~~~~~~~~~~~~~~~###~~"]
   :pages
   ["Carriers (C) are floating airports. They can\nhold up to 8 fighters and refuel them at sea.\n\nYou have a carrier in open water south of\nyour city."
    "Carrier operations:\n- Fighters land on carriers automatically\n  when they move onto the carrier's cell\n- Press U on a carrier to launch (wake)\n  all fighters aboard\n- Carriers move at speed 2"
    "Flight paths:\n- Press . on a cell to set a destination\n- Then press F on a city or carrier to\n  set a flight path\n- Fighters will automatically fly that route"
    "Mission:\n1. Build fighters at your city\n2. Fly them to the carrier\n3. Move the carrier south toward the island\n4. Launch fighters to scout\n\nCarriers extend your air power across oceans!"]})

(defn scenario-advanced []
  {:id :advanced
   :name "Advanced Orders"
   :description "Sentry, explore, and marching orders"
   :map-strings
   ["~~~~~~~~~~~~~~~~~~~~"
    "~~######~~~~~~~~~~~~"
    "~########~~~~~~~~~~~"
    "~###O####~~~~~~~~~~~"
    "~##A#O###~~~~~~~~~~~"
    "~########~~~~~~~~~~~"
    "~~######~~~~~~~~~~~~"
    "~~~~####~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~#####~~~~"
    "~~~~~~~~~~~#+###~~~~"]
   :pages
   ["Managing many units gets tedious. Empire has\nautomation commands to help.\n\nYou have two cities and an army to practice."
    "Sentry mode (S key):\n- Unit sleeps until an enemy appears nearby\n- Great for defensive positions\n- Press U on the cell to wake a sentry unit"
    "Explore mode (L key):\n- Army automatically explores nearby land\n- Walks the coastline and interior\n- Wakes up when it encounters an enemy"
    "Marching orders:\n1. Press . on a cell to set destination\n2. Press M on a city to set marching orders\n3. All new units from that city march there\n\nFlight paths work similarly (. then F on city)."
    "Try these commands:\n- Set one army to sentry (S)\n- Set marching orders on a city\n- Produce armies and watch them march!\n\nThese orders are essential for managing\na large empire."]})

(defn scenario-battle []
  {:id :battle
   :name "Mini Battle"
   :description "Full game against the computer on a small map"
   :map-strings
   ["~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~####~~~~~~~~~~~~~~~~~~~~~~~~"
    "~######~~~~~~~~~~~~~~~~~~~~~~~"
    "~##O####~~~~~~~~~~~~~~~~~~~~~~"
    "~#######~~~~~~~~~~~~~~~~~~~~~~"
    "~~#####~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~###~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~###~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~#####~~~"
    "~~~~~~~~~~~~~~~~~~~~~######~~~"
    "~~~~~~~~~~~~~~~~~~~~~####X#~~~"
    "~~~~~~~~~~~~~~~~~~~~~~#####~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~###~~~~"
    "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"]
   :pages
   ["This is a real battle! You face a computer\nopponent on a small map.\n\nYour city is in the north-west (green).\nThe enemy city is in the south-east (red)."
    "Strategy tips:\n- Build armies first to capture nearby cities\n- Use transports to cross water\n- Fighters are great for scouting\n- Patrol boats protect your transports"
    "Victory condition:\n- Destroy all enemy units and capture all\n  enemy cities\n\nThe computer will be building and attacking\ntoo. Good luck, Commander!"
    "Remember your commands:\n  QWEASDZXC = move\n  SPACE = skip turn\n  S = sentry, L = explore\n  P = pause, . = set destination\n  M = marching orders, F = flight path\n  ! = save game, ^ = load game\n\nPress [ESC] to hide this overlay and play!"]})

(def all-scenarios
  "Ordered vector of all tutorial scenario constructor functions."
  [scenario-movement
   scenario-production
   scenario-combat
   scenario-naval
   scenario-fighters
   scenario-carriers
   scenario-advanced
   scenario-battle])

(defn scenarios-list
  "Returns vector of {:id :name :description} for the tutorial menu."
  []
  (mapv (fn [ctor]
          (let [{:keys [id name description]} (ctor)]
            {:id id :name name :description description}))
        all-scenarios))

(defn get-scenario
  "Returns the full scenario map for the given keyword id, or nil."
  [scenario-id]
  (some (fn [ctor]
          (let [s (ctor)]
            (when (= scenario-id (:id s)) s)))
        all-scenarios))
