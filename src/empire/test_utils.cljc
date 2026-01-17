(ns empire.test-utils)

(defn- char->cell [c]
  (case c
    \s {:type :sea}
    \L {:type :land}
    \. nil
    \+ {:type :city :city-status :free}
    \O {:type :city :city-status :player}
    \X {:type :city :city-status :computer}
    \* {:type :land :waypoint true}
    \A {:type :land :contents {:type :army :owner :player}}
    \T {:type :sea :contents {:type :transport :owner :player}}
    \D {:type :sea :contents {:type :destroyer :owner :player}}
    \P {:type :sea :contents {:type :patrol-boat :owner :player}}
    \C {:type :sea :contents {:type :carrier :owner :player}}
    \B {:type :sea :contents {:type :battleship :owner :player}}
    \S {:type :sea :contents {:type :submarine :owner :player}}
    \F {:type :land :contents {:type :fighter :owner :player}}
    \J {:type :sea :contents {:type :fighter :owner :player}}
    (throw (ex-info (str "Unknown map char: " c) {:char c}))))

(defn build-test-map [strings]
  (atom (mapv (fn [row-str]
                (mapv char->cell row-str))
              strings)))

(def ^:private char->unit-type
  {\A :army
   \T :transport
   \D :destroyer
   \P :patrol-boat
   \C :carrier
   \B :battleship
   \S :submarine
   \F :fighter
   \J :fighter})

(defn- parse-unit-spec [unit-spec]
  (let [c (first unit-spec)
        n (if (> (count unit-spec) 1)
            (Integer/parseInt (subs unit-spec 1))
            1)]
    [(get char->unit-type c) n]))

(defn- find-unit-pos [game-map unit-type n]
  (let [positions (for [row-idx (range (count game-map))
                        col-idx (range (count (nth game-map row-idx)))
                        :let [cell (get-in game-map [row-idx col-idx])
                              contents (:contents cell)]
                        :when (and contents
                                   (= unit-type (:type contents))
                                   (= :player (:owner contents)))]
                    [row-idx col-idx])]
    (nth positions (dec n) nil)))

(defn set-test-unit [game-map-atom unit-spec & kvs]
  (let [[unit-type n] (parse-unit-spec unit-spec)
        pos (find-unit-pos @game-map-atom unit-type n)]
    (when (nil? pos)
      (throw (ex-info (str "Unit not found: " unit-spec) {:unit-spec unit-spec})))
    (swap! game-map-atom update-in (conj pos :contents) merge (apply hash-map kvs))))
