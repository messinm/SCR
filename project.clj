(defproject SCR "0.0.1"
  :description "swing clojure repl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main messinm.scr
  ;:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8030"]
  :dependencies [[clojure "1.4.0"]
                 [clj-ns-browser "1.2.0"]
                 [org.clojure/tools.nrepl "0.2.0-beta2"]]        
)
