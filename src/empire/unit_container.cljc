(ns empire.unit-container)

(defn get-count
  "Gets the total count of units in a container."
  [entity count-key]
  (get entity count-key 0))

(defn get-awake-count
  "Gets the count of awake units in a container."
  [entity awake-key]
  (get entity awake-key 0))

(defn has-awake?
  "Returns true if the container has any awake units."
  [entity awake-key]
  (pos? (get entity awake-key 0)))

(defn add-unit
  "Adds a sleeping unit to a container (for transport/carrier)."
  [entity count-key]
  (update entity count-key (fnil inc 0)))

(defn add-awake-unit
  "Adds an awake unit to a container (for city airports)."
  [entity count-key awake-key]
  (-> entity
      (update count-key (fnil inc 0))
      (update awake-key (fnil inc 0))))

(defn remove-awake-unit
  "Removes one awake unit from a container."
  [entity count-key awake-key]
  (-> entity
      (update count-key dec)
      (update awake-key dec)))

(defn wake-all
  "Wakes all units in a container."
  [entity count-key awake-key]
  (assoc entity awake-key (get entity count-key 0)))

(defn sleep-all
  "Puts all units in a container to sleep."
  [entity awake-key]
  (assoc entity awake-key 0))

(defn full?
  "Returns true if the container is at capacity."
  [entity count-key capacity]
  (>= (get entity count-key 0) capacity))
