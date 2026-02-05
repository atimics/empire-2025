(ns empire.performance
  "Performance monitoring: detects slow frames, profiles for 5 frames, writes report."
  (:require [empire.atoms :as atoms]
            [clojure.string :as str]))

;; Performance monitoring atoms
(def monitoring-active
  "True when we're actively collecting detailed timing data."
  (atom false))

(def monitoring-frames-remaining
  "Number of frames left to monitor."
  (atom 0))

(def monitoring-data
  "Vector of frame timing reports."
  (atom []))

(def current-frame-details
  "Accumulator for detailed timings within the current frame."
  (atom {}))

(def slow-frame-threshold-ms
  "Frame time in ms that triggers monitoring (1 second)."
  1000)

(def frames-to-monitor
  "Number of frames to monitor once triggered."
  5)

(defn- current-time-ms []
  (System/currentTimeMillis))

(defn nanos->ms [nanos]
  (/ nanos 1000000.0))

(defmacro timed
  "Executes body and returns [result elapsed-ms]."
  [& body]
  `(let [start# (System/nanoTime)
         result# (do ~@body)
         elapsed# (nanos->ms (- (System/nanoTime) start#))]
     [result# elapsed#]))

(defn record-detail!
  "Records a timing detail for the current frame. Category is a keyword, ms is elapsed time."
  [category ms]
  (when @monitoring-active
    (swap! current-frame-details update category
           (fn [existing]
             (if existing
               {:count (inc (:count existing))
                :total-ms (+ (:total-ms existing) ms)
                :max-ms (max (:max-ms existing) ms)}
               {:count 1 :total-ms ms :max-ms ms})))))

(defn start-monitoring!
  "Begins performance monitoring for the specified number of frames."
  []
  (reset! monitoring-data [])
  (reset! current-frame-details {})
  (reset! monitoring-frames-remaining frames-to-monitor)
  (reset! monitoring-active true)
  (println "Performance monitoring STARTED - slow frame detected!"))

(defn- format-timing-line [label ms total-ms]
  (let [pct (if (pos? total-ms) (* 100.0 (/ ms total-ms)) 0.0)]
    (format "  %-30s %8.2f ms  (%5.1f%%)" label ms pct)))

(defn- format-unit-details [unit-details]
  (when (seq unit-details)
    (let [sorted (sort-by (fn [[_ v]] (- (:total-ms v))) unit-details)]
      (concat
       ["  Unit processing breakdown:"]
       (map (fn [[category {:keys [count total-ms max-ms]}]]
              (format "    %-20s %3d calls  %8.2f ms total  %6.2f ms avg  %6.2f ms max"
                      (name category) count total-ms (/ total-ms count) max-ms))
            sorted)))))

(defn- format-frame-report [frame-num frame-data]
  (let [{:keys [total-ms update-player-map-ms update-computer-map-ms
                advance-game-batch-ms update-hover-status-ms
                advance-calls advance-details unit-details]} frame-data
        lines [(format "Frame %d: %.2f ms total" frame-num total-ms)
               (format-timing-line "update-player-map" update-player-map-ms total-ms)
               (format-timing-line "update-computer-map" update-computer-map-ms total-ms)
               (format-timing-line "advance-game-batch" advance-game-batch-ms total-ms)
               (format-timing-line "update-hover-status" update-hover-status-ms total-ms)
               (format "  advance-game called %d times" advance-calls)]
        unit-lines (format-unit-details unit-details)]
    (concat lines
            (when (seq unit-lines) unit-lines)
            (when (seq advance-details)
              (concat
               ["  Breakdown of advance-game calls:"]
               (map-indexed
                (fn [i detail]
                  (format "    [%d] %s: %.2f ms"
                          i (:action detail) (:ms detail)))
                advance-details))))))

(defn- generate-report []
  (let [header [""
                "=========================================="
                "PERFORMANCE MONITORING REPORT"
                "=========================================="
                ""
                (format "Triggered at round %d" @atoms/round-number)
                (format "Player items remaining: %d" (count @atoms/player-items))
                (format "Computer items remaining: %d" (count @atoms/computer-items))
                ""
                "Frame-by-frame breakdown:"
                ""]
        frame-reports (mapcat (fn [i data] (format-frame-report (inc i) data))
                              (range)
                              @monitoring-data)
        summary [""
                 "Summary:"
                 (format "  Frames monitored: %d" (count @monitoring-data))
                 (format "  Average frame time: %.2f ms"
                         (if (seq @monitoring-data)
                           (/ (reduce + (map :total-ms @monitoring-data))
                              (count @monitoring-data))
                           0.0))
                 (format "  Slowest frame: %.2f ms"
                         (if (seq @monitoring-data)
                           (apply max (map :total-ms @monitoring-data))
                           0.0))
                 ""
                 "Time distribution across all frames:"
                 (let [totals (reduce (fn [acc data]
                                        (-> acc
                                            (update :update-player-map + (:update-player-map-ms data))
                                            (update :update-computer-map + (:update-computer-map-ms data))
                                            (update :advance-game-batch + (:advance-game-batch-ms data))
                                            (update :update-hover-status + (:update-hover-status-ms data))
                                            (update :total + (:total-ms data))))
                                      {:update-player-map 0 :update-computer-map 0
                                       :advance-game-batch 0 :update-hover-status 0 :total 0}
                                      @monitoring-data)]
                   (str/join "\n"
                             [(format-timing-line "update-player-map" (:update-player-map totals) (:total totals))
                              (format-timing-line "update-computer-map" (:update-computer-map totals) (:total totals))
                              (format-timing-line "advance-game-batch" (:advance-game-batch totals) (:total totals))
                              (format-timing-line "update-hover-status" (:update-hover-status totals) (:total totals))]))
                 ""]]
    (str/join "\n" (concat header frame-reports summary))))

(defn write-report-and-abort!
  "Writes the performance report to a file and exits."
  []
  (let [timestamp (java.time.LocalDateTime/now)
        formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")
        filename (str "performance-" (.format timestamp formatter) ".txt")
        report (generate-report)]
    (spit filename report)
    (println "")
    (println "========================================")
    (println "PERFORMANCE MONITORING COMPLETE")
    (println (format "Report written to: %s" filename))
    (println "========================================")
    (println "")
    (println report)
    (println "")
    (println "Aborting game due to performance issues.")
    (System/exit 1)))

(defn record-frame-timing!
  "Records timing data for the current frame and handles monitoring lifecycle."
  [frame-data]
  (let [details @current-frame-details
        frame-data-with-details (assoc frame-data :unit-details details)]
    (reset! current-frame-details {})
    (swap! monitoring-data conj frame-data-with-details)
    (swap! monitoring-frames-remaining dec)
    (println (format "  Monitored frame %d/%d: %.2f ms"
                     (- frames-to-monitor @monitoring-frames-remaining)
                     frames-to-monitor
                     (:total-ms frame-data)))
    (when (<= @monitoring-frames-remaining 0)
      (write-report-and-abort!))))

(defn should-start-monitoring?
  "Returns true if frame-time-ms exceeds threshold and monitoring not active."
  [frame-time-ms]
  (and (not @monitoring-active)
       (> frame-time-ms slow-frame-threshold-ms)))
