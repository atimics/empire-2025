(ns empire.profiling
  (:require [clojure.java.io :as io]))

(def profiling-active (atom false))
(def frame-count (atom 0))
(def accumulated-times (atom {}))
(def profile-file "profile.txt")

(defn- write-profile! [text]
  (spit profile-file (str text "\n") :append true))

(defn toggle! []
  (swap! profiling-active not)
  (reset! frame-count 0)
  (reset! accumulated-times {})
  (when @profiling-active
    (spit profile-file ""))
  (let [msg (if @profiling-active "Profiling ON" "Profiling OFF")]
    (println msg)
    (write-profile! msg)))

(defn record! [section-name elapsed-ms]
  (when @profiling-active
    (swap! accumulated-times
           (fn [m]
             (let [[total cnt] (get m section-name [0.0 0])]
               (assoc m section-name [(+ total elapsed-ms) (inc cnt)]))))))

(defn format-report [times frame-cnt]
  (let [header (str "=== Profile (" frame-cnt " frames) ===")
        entries (sort-by (fn [[_ [total cnt]]] (- (/ total (max cnt 1))))
                         times)
        lines (map (fn [[name [total cnt]]]
                     (let [avg (/ total (max cnt 1))]
                       (format "  %-24s avg: %6.2f ms  total: %6.1f ms  calls: %d"
                               name avg total cnt)))
                   entries)]
    (str header "\n" (clojure.string/join "\n" lines))))

(defn end-frame! []
  (when @profiling-active
    (swap! frame-count inc)
    (when (>= @frame-count 30)
      (let [report (format-report @accumulated-times @frame-count)]
        (println report)
        (write-profile! report))
      (reset! frame-count 0)
      (reset! accumulated-times {}))))

(defmacro profile [section-name & body]
  `(if @profiling-active
     (let [start# (System/nanoTime)
           result# (do ~@body)
           elapsed# (/ (- (System/nanoTime) start#) 1000000.0)]
       (record! ~section-name elapsed#)
       result#)
     (do ~@body)))
