(ns empire.save-load
  (:require [empire.atoms :as atoms]))

(def saveable-atoms
  "Map of atom names to atoms that should be saved/restored."
  {:game-map atoms/game-map
   :player-map atoms/player-map
   :computer-map atoms/computer-map
   :production atoms/production
   :destination atoms/destination
   :round-number atoms/round-number
   :cells-needing-attention atoms/cells-needing-attention
   :player-items atoms/player-items
   :computer-items atoms/computer-items
   :waiting-for-input atoms/waiting-for-input
   :paused atoms/paused
   :computer-turn atoms/computer-turn
   :next-transport-id atoms/next-transport-id
   :next-country-id atoms/next-country-id
   :next-unload-event-id atoms/next-unload-event-id
   :next-destroyer-id atoms/next-destroyer-id
   :next-carrier-id atoms/next-carrier-id
   :next-escort-id atoms/next-escort-id
   :sea-lane-network atoms/sea-lane-network
   :claimed-objectives atoms/claimed-objectives
   :claimed-transport-targets atoms/claimed-transport-targets
   :fighter-leg-records atoms/fighter-leg-records
   :coast-walkers-produced atoms/coast-walkers-produced
   :distant-city-pairs atoms/distant-city-pairs})

(defn list-save-files
  "Returns a vector of save filenames sorted by modification time (newest first).
   If dir-path is not provided, defaults to 'saves'."
  ([] (list-save-files "saves"))
  ([dir-path]
   (let [dir (java.io.File. dir-path)]
     (if (.exists dir)
       (->> (.listFiles dir)
            (filter #(.endsWith (.getName %) ".edn"))
            (sort-by #(- (.lastModified %)))
            (mapv #(.getName %)))
       []))))
