;
;   Swing Clojure REPL            
;
;   Copyright (c) Mike Messinides. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;
;   You must not remove this notice, or any other, from this software.
;
;

(ns messinm.scr
"Swing Clojure REPL"
  (:require [clojure.tools.nrepl :as repl])
  (:use [clojure.tools.nrepl.server :only (start-server stop-server)])
  (:import 
	  (javax.swing JFrame JLabel JTextArea JTextPane JTextField JButton JScrollPane SwingConstants
                      JSplitPane JPanel BoxLayout UIManager KeyStroke SwingUtilities) 
	  (javax.swing.tree TreeModel) 
        (javax.swing.event CaretEvent CaretListener)
        (javax.swing.text DefaultStyledDocument StyleConstants StyleConstants$CharacterConstants SimpleAttributeSet)
        (java.awt.event ActionListener WindowListener MouseAdapter KeyEvent) 
        (java.awt GridLayout FlowLayout Component Dimension Font EventQueue)
        (java.util.concurrent SynchronousQueue TimeUnit) 
        (java.io StringReader PushbackReader FileReader ByteArrayOutputStream PrintStream LineNumberReader)))

(def title "Swing Clojure REPL")
(def ver-string ";SCR v0.0.1")
(. UIManager (setLookAndFeel (. UIManager (getSystemLookAndFeelClassName))))
(def code-font (new Font "Courier" (. Font PLAIN) 12))
(def new-char-offset (atom -1))
(def plain-attr (new SimpleAttributeSet))
(def bold-attr (new SimpleAttributeSet))
(StyleConstants/setBold bold-attr true)
(StyleConstants/setBackground bold-attr java.awt.Color/CYAN)
(defn closing? [#^String c] (or (= c ")") (= c "]") (= c "}")))
(defn match? [#^String cls #^String opn] (cond 
                                            (= cls ")") (= opn "(")
                                            (= cls "]") (= opn "[")
                                            (= cls "}") (= opn "{") 
                                             :default    false))

(defn parmatch-doc
"returns [doc listnr] where doc is a StyledDocument and listnr is a CaretListener that together 
implement paren, bracket and brace highlighting"   
[]
(let [doc       (new  DefaultStyledDocument)
      _         (.insertString doc 0 ver-string nil)
      listnr    (proxy [CaretListener, Runnable] []
                     (caretUpdate [#^CaretEvent e] 
                          (let [char-offset (- (.getDot e) 1)
                                c-char     (if (>= char-offset 0) (.getText doc char-offset 1))     
                                new-offset  (if (and c-char (> char-offset 0) (closing? c-char)) 
                                              (loop [depth 0 open-offset (- char-offset 1)]						     
                                                (if (< depth 0) (+ open-offset 1)
                                                  (if (= open-offset -1) -1 
                                                     (let [xchar (.getText doc open-offset 1)]
                                                        (recur (if (= xchar c-char) (+ depth 1) 
                                                           (if (match? c-char xchar) (- depth 1) depth))
                                                              (- open-offset 1) )))))
                                                -1)]
                               (swap! new-char-offset (fn[_] new-offset)))
                            (EventQueue/invokeLater  this))
                     (run [] 
                         (. doc (setCharacterAttributes 0 (. doc (getLength)) plain-attr true))
                         (if (>= @new-char-offset 0) 
                            (do (. doc (setCharacterAttributes @new-char-offset 1 bold-attr true))))
                         (swap! new-char-offset (fn[_] -1))))]
     [doc listnr]))

(def runrepl (atom true))
(def eval-end (proxy [WindowListener] []
                   (windowActivated [e])
			          (windowClosed [e] (swap! runrepl (fn[x] false)))
                   (windowClosing [e])
                   (windowDeactivated [e])
                   (windowDeiconified [e])
                   (windowIconified [e])
                   (windowOpened [e])))



(defn no-wrap-text-pane
[doc] (proxy [JTextPane] [doc]
        (getScrollableTracksViewportWidth [] false)))


;;;;;;;;;;;;;;;Main frame;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn main-frame
[eval-fn init-map]
(let [frame (new JFrame title)
      doc-lstnr (parmatch-doc) 
      doc     (first doc-lstnr)
      entry-text (doto (no-wrap-text-pane doc) 
                         (.setFont code-font) 
                         (.addCaretListener (second doc-lstnr)))
      txt-pref-dim    (new Dimension 400,300)
      treetxt-pref-dim    (new Dimension 300,250)
      tree-pref-dim    (new Dimension 300,350)
      result-text (doto  (new JTextArea) (.setFont code-font))
      entrypane (doto (new JScrollPane entry-text) (.setPreferredSize txt-pref-dim))
      resultpane (doto (new JScrollPane result-text) (.setPreferredSize txt-pref-dim))
      entry-lbl   (new JLabel (str (:ns init-map)))
      clr-button (new JButton "clear results")
      eval-button (new JButton "eval") 
      entryEvalPane (new JPanel)
      resultClrPane (new JPanel)
      entryEvalPane          (doto entryEvalPane (.setLayout (new BoxLayout entryEvalPane BoxLayout/PAGE_AXIS)) (.add entry-lbl) 
                                                   (.add entrypane) (.add eval-button)) 
      resultClrPane          (doto resultClrPane (.setLayout (new BoxLayout resultClrPane BoxLayout/PAGE_AXIS)) (.add resultpane) (.add clr-button))
      evalSplitPane  (new JSplitPane JSplitPane/VERTICAL_SPLIT entryEvalPane resultClrPane)]
    (.setMnemonic eval-button KeyEvent/VK_E)   
    (.setMnemonic clr-button KeyEvent/VK_L) 
    (.addActionListener eval-button 
           (proxy [ActionListener] [] 
                (actionPerformed [evt]
                    (let [e-m (eval-fn (or (.getSelectedText entry-text) (.getText entry-text)) (.getText entry-lbl))
                          ns (:ns e-m)]
                         (.append result-text (:out e-m))
                         (when ns (.setText entry-lbl (str ns)))))))
    (.addActionListener clr-button 
           (proxy [ActionListener] [] 
                (actionPerformed [evt]
                      (. result-text (setText "")))))

      (.setBackground (.getViewport entrypane) java.awt.Color/WHITE)

      (.addMouseListener (.getViewport entrypane)  (proxy [MouseAdapter] [] 
                                    (mousePressed [mevent]
                                        (.requestFocusInWindow entry-text ))))


      (doto frame 
                (.add evalSplitPane) 
                (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)		
                (.setSize 700 700)
                (.addWindowListener eval-end)
                (.pack)
                (.setVisible true)) 
                frame ))


(defn resp-vals-errs
"transforms the supplied seq of nrepl responses and returns a map with keys :out (output and error from the eval)
and :ns (namespace after eval)"
[responses]
{:out (->> responses
                (map #(or (:out %) (:value %) (:err %)))
                  (filter #(not (nil? %))))
 :ns (reduce #(or (:ns %2) %1) nil responses)})
 
(defn -main
[& args]
(with-open [srvr (start-server :port 0)]
  (let [p    (.getLocalPort (:ss @srvr))
        _    (println "port " p)]
   (with-open [conn (repl/connect :port p)]
   	(let [client 	(repl/client conn 1000)
    	      eval-fn 	(fn[txt ns] 
    	                  (let [r-m (repl/message client {:op :eval :code txt :ns (str ns)})
    	                               ; _    (println r-m)
    	                          	     r-v  (resp-vals-errs r-m)]
    	                               ; _    (println r-v)]
    	                      {:out (reduce (fn[t r] (str t r "\n")) "" (:out r-v)) 
    	                               :ns (:ns r-v)}))
    	      init     (eval-fn "*ns*" "user")
    	  		frame    (SwingUtilities/invokeLater #(main-frame eval-fn init))]
    	  	(loop [go? true]
    	  	  (Thread/sleep 200)
    	  	  (if go? (recur @runrepl)))))))
    	  	  (swap! runrepl (fn[x] true)))
    	  	  



  
