(ns empire.profile
  "Profiling utilities for render performance analysis."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.rendering :as rendering]
            [empire.unit-container :as uc]))

(defn make-test-map
  "Creates a test map of given dimensions with mixed terrain."
  [cols rows]
  (vec (for [col (range cols)]
         (vec (for [row (range rows)]
                (let [terrain (if (< (rand) 0.3) :sea :land)]
                  (cond-> {:type terrain}
                    (and (= terrain :land) (< (rand) 0.1))
                    (assoc :type :city :city-status :player)

                    (and (= terrain :land) (< (rand) 0.05))
                    (assoc :contents {:type :army :mode :awake :owner :player}))))))))

(defn time-iterations
  "Times n iterations of f, returns avg ms."
  [n f]
  (let [start (System/nanoTime)]
    (dotimes [_ n] (f))
    (/ (- (System/nanoTime) start) (* n 1e6))))

(defn profile-data-access
  "Profile get-in access patterns."
  [the-map iterations]
  (let [cols (count the-map)
        rows (count (first the-map))
        ;; Vector get-in
        vec-time (time-iterations iterations
                   #(doseq [col (range cols)
                            row (range rows)]
                      (get-in the-map [col row])))
        ;; Direct vector access
        direct-time (time-iterations iterations
                      #(doseq [col (range cols)
                               row (range rows)]
                        (-> the-map (nth col) (nth row))))
        ;; Java 2D array (with type hints to avoid reflection)
        ^"[[Ljava.lang.Object;" arr (to-array-2d the-map)
        array-time (time-iterations iterations
                     #(doseq [col (range cols)
                              row (range rows)]
                       (aget ^objects (aget arr col) row)))]
    {:vector-get-in vec-time
     :vector-direct direct-time
     :java-array array-time
     :cells (* cols rows)}))

(defn profile-color-lookup
  "Profile color-of function."
  [the-map iterations]
  (let [cols (count the-map)
        rows (count (first the-map))
        time-ms (time-iterations iterations
                  #(doseq [col (range cols)
                           row (range rows)]
                    (let [cell (get-in the-map [col row])]
                      (rendering/color-of cell))))]
    {:color-lookup-ms time-ms
     :cells (* cols rows)}))

(defn profile-atom-deref
  "Profile atom dereference overhead."
  [iterations]
  (let [cells-time (time-iterations iterations
                     #(dotimes [_ 1000]
                        @atoms/cells-needing-attention))
        prod-time (time-iterations iterations
                    #(dotimes [_ 1000]
                       @atoms/production))]
    {:cells-needing-attention-per-1000 cells-time
     :production-per-1000 prod-time}))

(defn run-profile
  "Run all profiling tests."
  [cols rows iterations]
  (println (str "Profiling " cols "x" rows " map (" (* cols rows) " cells), " iterations " iterations each\n"))
  (let [test-map (make-test-map cols rows)]

    (println "=== Data Access ===")
    (let [results (profile-data-access test-map iterations)]
      (println (format "  get-in:       %.3f ms" (:vector-get-in results)))
      (println (format "  nth/nth:      %.3f ms" (:vector-direct results)))
      (println (format "  Java array:   %.3f ms" (:java-array results)))
      (println (format "  Array speedup: %.1fx" (/ (:vector-get-in results) (:java-array results)))))

    (println "\n=== Color Lookup ===")
    (let [results (profile-color-lookup test-map iterations)]
      (println (format "  color-of:     %.3f ms" (:color-lookup-ms results))))

    (println "\n=== Atom Derefs (per 1000) ===")
    (let [results (profile-atom-deref iterations)]
      (println (format "  cells-needing-attention: %.3f ms" (:cells-needing-attention-per-1000 results)))
      (println (format "  production:              %.3f ms" (:production-per-1000 results))))

    (println "\n=== Summary ===")
    (println "  Note: Quil draw calls (q/fill, q/rect, q/text) cannot be profiled without graphics context.")))

(defn -main [& args]
  (reset! atoms/cells-needing-attention [])
  (reset! atoms/production {})
  (run-profile 100 60 100))
