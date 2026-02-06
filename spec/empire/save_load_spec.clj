(ns empire.save-load-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.save-load :as save-load]
            [empire.test-utils :refer [reset-all-atoms!]]))

(describe "load menu atoms"
  (before (reset-all-atoms!))

  (it "load-menu-open defaults to false"
    (should= false @atoms/load-menu-open))

  (it "load-menu-files defaults to empty vector"
    (should= [] @atoms/load-menu-files))

  (it "load-menu-hovered defaults to nil"
    (should-be-nil @atoms/load-menu-hovered)))

(describe "list-save-files"
  (it "returns empty vector when saves directory doesn't exist"
    (should= [] (save-load/list-save-files "nonexistent-dir")))

  (it "returns empty vector when saves directory is empty"
    (let [dir (java.io.File/createTempFile "saves" "")]
      (.delete dir)
      (.mkdir dir)
      (try
        (should= [] (save-load/list-save-files (.getPath dir)))
        (finally
          (.delete dir)))))

  (it "returns edn files sorted newest first"
    (let [dir (java.io.File/createTempFile "saves" "")]
      (.delete dir)
      (.mkdir dir)
      (try
        (let [f1 (java.io.File. dir "save-2026-01-01-120000.edn")
              f2 (java.io.File. dir "save-2026-01-02-120000.edn")]
          (spit f1 "{}")
          (Thread/sleep 10)
          (spit f2 "{}")
          (let [files (save-load/list-save-files (.getPath dir))]
            (should= 2 (count files))
            (should= "save-2026-01-02-120000.edn" (first files))
            (should= "save-2026-01-01-120000.edn" (second files))))
        (finally
          (doseq [f (.listFiles dir)] (.delete f))
          (.delete dir))))))

(describe "saveable-atoms"
  (it "contains game-map"
    (should-contain :game-map save-load/saveable-atoms))

  (it "contains production"
    (should-contain :production save-load/saveable-atoms))

  (it "contains round-number"
    (should-contain :round-number save-load/saveable-atoms))

  (it "does not contain transient atoms like last-key"
    (should-not-contain :last-key save-load/saveable-atoms))

  (it "does not contain load-menu-open"
    (should-not-contain :load-menu-open save-load/saveable-atoms)))
