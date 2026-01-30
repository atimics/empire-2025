(ns empire.computer
  "Computer AI coordinator - dispatches to specialized modules for unit processing.
   Gutted for CommandingGeneral refactor - units currently do nothing."
  (:require [empire.atoms :as atoms]
            [empire.computer.army :as army]
            [empire.computer.fighter :as fighter]
            [empire.computer.ship :as ship]
            [empire.computer.transport :as transport]
            [empire.profiling :as profiling]))

;; Main dispatch function

(defn process-computer-unit
  "Processes a single computer unit's turn. Returns nil when done.
   Currently all units do nothing - awaiting CommandingGeneral implementation."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= (:owner unit) :computer))
      (case (:type unit)
        :army (profiling/profile "cpu-army" (army/process-army pos))
        :fighter (profiling/profile "cpu-fighter" (fighter/process-fighter pos unit))
        :transport (profiling/profile "cpu-transport" (transport/process-transport pos))
        (:destroyer :submarine :patrol-boat :carrier :battleship)
        (profiling/profile "cpu-ship" (ship/process-ship pos (:type unit)))
        ;; Satellite - no processing needed
        nil))))
