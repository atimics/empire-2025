(ns empire.profiling-spec
  (:require [speclj.core :refer :all]
            [empire.profiling :as profiling]
            [clojure.java.io :as io]))

(describe "toggle!"
  (before
    (reset! profiling/profiling-active false)
    (reset! profiling/frame-count 0)
    (reset! profiling/accumulated-times {}))
  (after
    (reset! profiling/profiling-active false)
    (let [f (io/file profiling/profile-file)]
      (when (.exists f) (.delete f))))

  (it "flips profiling-active from false to true"
    (profiling/toggle!)
    (should= true @profiling/profiling-active))

  (it "flips profiling-active from true to false"
    (reset! profiling/profiling-active true)
    (profiling/toggle!)
    (should= false @profiling/profiling-active))

  (it "resets accumulated-times on toggle on"
    (reset! profiling/accumulated-times {"foo" [100.0 5]})
    (profiling/toggle!)
    (should= {} @profiling/accumulated-times))

  (it "resets frame-count on toggle on"
    (reset! profiling/frame-count 15)
    (profiling/toggle!)
    (should= 0 @profiling/frame-count))

  (it "creates profile.txt when toggled on"
    (profiling/toggle!)
    (should (.exists (io/file profiling/profile-file)))))

(describe "record!"
  (before
    (reset! profiling/profiling-active true)
    (reset! profiling/accumulated-times {}))

  (it "accumulates total and count when profiling is active"
    (profiling/record! "test-section" 1.5)
    (should= [1.5 1] (get @profiling/accumulated-times "test-section")))

  (it "accumulates multiple calls"
    (profiling/record! "test-section" 1.5)
    (profiling/record! "test-section" 2.5)
    (should= [4.0 2] (get @profiling/accumulated-times "test-section")))

  (it "is a no-op when profiling is inactive"
    (reset! profiling/profiling-active false)
    (profiling/record! "test-section" 1.5)
    (should= {} @profiling/accumulated-times)))

(describe "format-report"
  (it "returns expected formatted string"
    (let [times {"alpha" [30.0 30] "beta" [90.0 30]}
          report (profiling/format-report times 30)]
      (should-contain "Profile (30 frames)" report)
      (should-contain "beta" report)
      (should-contain "alpha" report)))

  (it "sorts by average descending"
    (let [times {"slow" [90.0 30] "fast" [30.0 30]}
          report (profiling/format-report times 30)
          slow-idx (.indexOf report "slow")
          fast-idx (.indexOf report "fast")]
      (should (< slow-idx fast-idx))))

  (it "handles empty times map"
    (let [report (profiling/format-report {} 30)]
      (should-contain "Profile (30 frames)" report))))

(describe "profile macro"
  (before
    (reset! profiling/profiling-active true)
    (reset! profiling/accumulated-times {}))

  (it "returns the body's value"
    (let [result (profiling/profile "test" (+ 1 2))]
      (should= 3 result)))

  (it "records timing when active"
    (profiling/profile "test" (Thread/sleep 1))
    (let [[total count] (get @profiling/accumulated-times "test")]
      (should (> total 0))
      (should= 1 count)))

  (it "returns body value when inactive"
    (reset! profiling/profiling-active false)
    (let [result (profiling/profile "test" (+ 1 2))]
      (should= 3 result)))

  (it "does not record when inactive"
    (reset! profiling/profiling-active false)
    (profiling/profile "test" (+ 1 2))
    (should= {} @profiling/accumulated-times)))

(describe "end-frame!"
  (before
    (reset! profiling/profiling-active true)
    (reset! profiling/frame-count 0)
    (reset! profiling/accumulated-times {}))
  (after
    (reset! profiling/profiling-active false)
    (let [f (io/file profiling/profile-file)]
      (when (.exists f) (.delete f))))

  (it "increments frame-count when active"
    (profiling/end-frame!)
    (should= 1 @profiling/frame-count))

  (it "does not increment when inactive"
    (reset! profiling/profiling-active false)
    (profiling/end-frame!)
    (should= 0 @profiling/frame-count))

  (it "resets accumulators after 30 frames"
    (reset! profiling/frame-count 29)
    (profiling/record! "test" 1.0)
    (profiling/end-frame!)
    (should= 0 @profiling/frame-count)
    (should= {} @profiling/accumulated-times))

  (it "writes report to profile.txt after 30 frames"
    (reset! profiling/frame-count 29)
    (profiling/record! "test" 1.0)
    (profiling/end-frame!)
    (let [content (slurp profiling/profile-file)]
      (should-contain "Profile (30 frames)" content)
      (should-contain "test" content))))
