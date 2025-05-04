import javax.swing.*; 

import javax.swing.event.*; 

import javax.swing.Timer; 

import java.awt.*; 

import java.awt.event.*; 

import java.io.*; 

import java.util.*; 

import java.util.List; 

 

public class SmartTextEditor extends JFrame { 

    // Core data structures 

    private Stack<String> undoStack; 

    private Stack<String> redoStack; 

    private Trie dictionary; 

    private EditHistory editHistory; 

     

    // GUI Components 

    private JTextArea textArea; 

    private JList<String> suggestionList; 

    private JLabel statusLabel; 

    private DefaultListModel<String> suggestionModel; 

    private javax.swing.Timer typingTimer; 

    private boolean isProcessingUndo; 

    private boolean isProcessingRedo; 

     

    // File handling 

    private File currentFile; 

    private boolean documentChanged; 

    private JFileChooser fileChooser; 

     

    public SmartTextEditor() { 

        // Initialize data structures 

        undoStack = new Stack<>(); 

        redoStack = new Stack<>(); 

        dictionary = new Trie(); 

        editHistory = new EditHistory(); 

        fileChooser = new JFileChooser(); 

        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() { 

            @Override 

            public boolean accept(File f) { 

                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt") ||  

                       f.getName().toLowerCase().endsWith(".java"); 

            } 

             

            @Override 

            public String getDescription() { 

                return "Text Files (*.txt) or Java Files (*.java)"; 

            } 

        }); 

         

        // Load dictionary with common words 

        loadDictionary(); 

         

        // Set up the frame 

        setTitle("Smart Text Editor"); 

        setSize(800, 600); 

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 

        addWindowListener(new WindowAdapter() { 

            @Override 

            public void windowClosing(WindowEvent e) { 

                exitApplication(); 

            } 

        }); 

         

        // Create components 

        setupUI(); 

         

        // Register keyboard shortcuts 

        setupKeyboardShortcuts(); 

         

        // Add initial empty state to undo stack 

        undoStack.push(""); 

        editHistory.addEdit(""); 

        documentChanged = false; 

         

        // Show the frame 

        setLocationRelativeTo(null); 

        setVisible(true); 

    } 

     

    private void setupUI() { 

        // Main text area 

        textArea = new JTextArea(); 

        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14)); 

        textArea.setLineWrap(true); 

        textArea.setWrapStyleWord(true); 

        JScrollPane scrollPane = new JScrollPane(textArea); 

         

        // Suggestions panel 

        suggestionModel = new DefaultListModel<>(); 

        suggestionList = new JList<>(suggestionModel); 

        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); 

        JScrollPane suggestionScrollPane = new JScrollPane(suggestionList); 

        suggestionScrollPane.setPreferredSize(new Dimension(150, 0)); 

         

        // Status bar 

        statusLabel = new JLabel("Ready"); 

        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); 

         

        // Create split pane for main area and suggestions 

        JSplitPane splitPane = new JSplitPane( 

                JSplitPane.HORIZONTAL_SPLIT, 

                scrollPane, 

                suggestionScrollPane 

        ); 

        splitPane.setResizeWeight(0.8); 

         

        // Main content panel 

        JPanel contentPanel = new JPanel(new BorderLayout()); 

        contentPanel.add(splitPane, BorderLayout.CENTER); 

        contentPanel.add(statusLabel, BorderLayout.SOUTH); 

         

        // Add to frame 

        setContentPane(contentPanel); 

         

        // Create menu bar 

        createMenuBar(); 

         

        // Setup listeners 

        setupListeners(); 

         

        // Typing timer for auto-suggestions 

        typingTimer = new javax.swing.Timer(300, e -> updateSuggestions()); 

        typingTimer.setRepeats(false); 

    } 

     

    private void createMenuBar() { 

        JMenuBar menuBar = new JMenuBar(); 

         

        // File menu 

        JMenu fileMenu = new JMenu("File"); 

         

        JMenuItem newMenuItem = new JMenuItem("New"); 

        newMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)); 

        newMenuItem.addActionListener(e -> newFile()); 

        fileMenu.add(newMenuItem); 

         

        JMenuItem openMenuItem = new JMenuItem("Open"); 

        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)); 

        openMenuItem.addActionListener(e -> openFile()); 

        fileMenu.add(openMenuItem); 

         

        JMenuItem saveMenuItem = new JMenuItem("Save"); 

        saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)); 

        saveMenuItem.addActionListener(e -> saveFile(false)); 

        fileMenu.add(saveMenuItem); 

         

        JMenuItem saveAsMenuItem = new JMenuItem("Save As..."); 

        saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,  

                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)); 

        saveAsMenuItem.addActionListener(e -> saveFile(true)); 

        fileMenu.add(saveAsMenuItem); 

         

        fileMenu.addSeparator(); 

         

        JMenuItem exitMenuItem = new JMenuItem("Exit"); 

        exitMenuItem.addActionListener(e -> exitApplication()); 

        fileMenu.add(exitMenuItem); 

         

        // Edit menu 

        JMenu editMenu = new JMenu("Edit"); 

         

        JMenuItem undoMenuItem = new JMenuItem("Undo"); 

        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)); 

        undoMenuItem.addActionListener(e -> undo()); 

        editMenu.add(undoMenuItem); 

         

        JMenuItem redoMenuItem = new JMenuItem("Redo"); 

        redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK)); 

        redoMenuItem.addActionListener(e -> redo()); 

        editMenu.add(redoMenuItem); 

         

        editMenu.addSeparator(); 

         

        JMenuItem cutMenuItem = new JMenuItem("Cut"); 

        cutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)); 

        cutMenuItem.addActionListener(e -> textArea.cut()); 

        editMenu.add(cutMenuItem); 

         

        JMenuItem copyMenuItem = new JMenuItem("Copy"); 

        copyMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)); 

        copyMenuItem.addActionListener(e -> textArea.copy()); 

        editMenu.add(copyMenuItem); 

         

        JMenuItem pasteMenuItem = new JMenuItem("Paste"); 

        pasteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK)); 

        pasteMenuItem.addActionListener(e -> textArea.paste()); 

        editMenu.add(pasteMenuItem); 

         

        JMenuItem selectAllMenuItem = new JMenuItem("Select All"); 

        selectAllMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)); 

        selectAllMenuItem.addActionListener(e -> textArea.selectAll()); 

        editMenu.add(selectAllMenuItem); 

         

        // History menu 

        JMenu historyMenu = new JMenu("History"); 

         

        JMenuItem previousEditMenuItem = new JMenuItem("Previous Edit"); 

        previousEditMenuItem.addActionListener(e -> navigateToEdit(false)); 

        historyMenu.add(previousEditMenuItem); 

         

        JMenuItem nextEditMenuItem = new JMenuItem("Next Edit"); 

        nextEditMenuItem.addActionListener(e -> navigateToEdit(true)); 

        historyMenu.add(nextEditMenuItem); 

         

        // Add menus to menu bar 

        menuBar.add(fileMenu); 

        menuBar.add(editMenu); 

        menuBar.add(historyMenu); 

         

        setJMenuBar(menuBar); 

    } 

     

    private void setupListeners() { 

        // Document listener for text changes 

        textArea.getDocument().addDocumentListener(new DocumentListener() { 

            @Override 

            public void insertUpdate(DocumentEvent e) { 

                handleTextChange(); 

            } 

             

            @Override 

            public void removeUpdate(DocumentEvent e) { 

                handleTextChange(); 

            } 

             

            @Override 

            public void changedUpdate(DocumentEvent e) { 

                // Plain text components don't fire these events 

            } 

        }); 

         

        // Suggestion list selection 

        suggestionList.addListSelectionListener(e -> { 

            if (!e.getValueIsAdjusting() && suggestionList.getSelectedIndex() != -1) { 

                applySuggestion(suggestionList.getSelectedValue()); 

            } 

        }); 

    } 

     

    private void setupKeyboardShortcuts() { 

        InputMap inputMap = textArea.getInputMap(JComponent.WHEN_FOCUSED); 

        ActionMap actionMap = textArea.getActionMap(); 

         

        // Undo - Ctrl+Z 

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo"); 

        actionMap.put("undo", new AbstractAction() { 

            @Override 

            public void actionPerformed(ActionEvent e) { 

                undo(); 

            } 

        }); 

         

        // Redo - Ctrl+Y 

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo"); 

        actionMap.put("redo", new AbstractAction() { 

            @Override 

            public void actionPerformed(ActionEvent e) { 

                redo(); 

            } 

        }); 

    } 

     

    private void handleTextChange() { 

        if (!isProcessingUndo && !isProcessingRedo) { 

            // Reset the typing timer 

            if (typingTimer.isRunning()) { 

                typingTimer.restart(); 

            } else { 

                typingTimer.start(); 

            } 

             

            // Save state for undo 

            String currentText = textArea.getText(); 

            if (!undoStack.isEmpty() && !currentText.equals(undoStack.peek())) { 

                undoStack.push(currentText); 

                editHistory.addEdit(currentText); 

                redoStack.clear(); 

                documentChanged = true; 

                updateStatusBar(); 

                updateTitle(); 

            } 

        } 

    } 

     

    private void updateSuggestions() { 

        try { 

            // Find the word being typed 

            int caretPosition = textArea.getCaretPosition(); 

            String text = textArea.getText(); 

             

            if (text.isEmpty() || caretPosition == 0) { 

                suggestionModel.clear(); 

                return; 

            } 

             

            // Find the start of the current word 

            int start = caretPosition - 1; 

            while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) { 

                start--; 

            } 

            start++; 

             

            // Extract the current prefix 

            if (start < caretPosition) { 

                String prefix = text.substring(start, caretPosition); 

                 

                if (prefix.length() >= 2) { // Only suggest for 2+ characters 

                    List<String> suggestions = dictionary.getSuggestions(prefix.toLowerCase()); 

                    suggestionModel.clear(); 

                     

                    for (String suggestion : suggestions) { 

                        suggestionModel.addElement(suggestion); 

                    } 

                } else { 

                    suggestionModel.clear(); 

                } 

            } 

        } catch (Exception e) { 

            System.err.println("Error updating suggestions: " + e.getMessage()); 

            suggestionModel.clear(); 

        } 

    } 

     

    private void applySuggestion(String suggestion) { 

        try { 

            // Find the word being replaced 

            int caretPosition = textArea.getCaretPosition(); 

            String text = textArea.getText(); 

             

            int start = caretPosition - 1; 

            while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) { 

                start--; 

            } 

            start++; 

             

            // Replace the prefix with the suggestion 

            if (start < caretPosition) { 

                textArea.getDocument().remove(start, caretPosition - start); 

                textArea.getDocument().insertString(start, suggestion, null); 

                 

                // Clear suggestions 

                suggestionModel.clear(); 

            } 

        } catch (Exception e) { 

            System.err.println("Error applying suggestion: " + e.getMessage()); 

        } 

    } 

     

    private void undo() { 

        if (undoStack.size() > 1) { // Keep at least one element in undo stack 

            isProcessingUndo = true; 

            redoStack.push(undoStack.pop()); 

            textArea.setText(undoStack.peek()); 

            isProcessingUndo = false; 

            documentChanged = true; 

            updateStatusBar(); 

            updateTitle(); 

        } 

    } 

     

    private void redo() { 

        if (!redoStack.isEmpty()) { 

            isProcessingRedo = true; 

            String redoText = redoStack.pop(); 

            undoStack.push(redoText); 

            textArea.setText(redoText); 

            isProcessingRedo = false; 

            documentChanged = true; 

            updateStatusBar(); 

            updateTitle(); 

        } 

    } 

     

    private void navigateToEdit(boolean forward) { 

        String currentText = textArea.getText(); 

        String newText; 

         

        if (forward) { 

            newText = editHistory.getNextEdit(currentText); 

        } else { 

            newText = editHistory.getPreviousEdit(currentText); 

        } 

         

        if (newText != null && !newText.equals(currentText)) { 

            isProcessingUndo = true; // Prevent triggering new history entries 

            textArea.setText(newText); 

            undoStack.push(newText); 

            isProcessingUndo = false; 

            documentChanged = true; 

            updateStatusBar(); 

            updateTitle(); 

        } 

    } 

     

    private void updateStatusBar() { 

        int undoAvailable = Math.max(0, undoStack.size() - 1); 

        int redoAvailable = redoStack.size(); 

        int totalEdits = editHistory.size(); 

        int currentEdit = editHistory.getCurrentPosition() + 1; 

         

        statusLabel.setText(String.format("Edits: %d/%d | Undo: %d | Redo: %d",  

            currentEdit, totalEdits, undoAvailable, redoAvailable)); 

    } 

     

    private void updateTitle() { 

        String title = "Smart Text Editor"; 

        if (currentFile != null) { 

            title += " - " + currentFile.getName(); 

        } 

        if (documentChanged) { 

            title += " *"; 

        } 

        setTitle(title); 

    } 

     

    // File operations 

    private void newFile() { 

        if (documentChanged) { 

            int response = JOptionPane.showConfirmDialog(this, 

                "Current document has unsaved changes. Save changes?", 

                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION); 

             

            if (response == JOptionPane.YES_OPTION) { 

                if (!saveFile(false)) { 

                    return; // User cancelled save operation 

                } 

            } else if (response == JOptionPane.CANCEL_OPTION) { 

                return; // User cancelled operation 

            } 

        } 

         

        textArea.setText(""); 

        currentFile = null; 

        undoStack.clear(); 

        redoStack.clear(); 

        editHistory.clear(); 

        undoStack.push(""); 

        editHistory.addEdit(""); 

        documentChanged = false; 

        updateStatusBar(); 

        updateTitle(); 

    } 

     

    private void openFile() { 

        if (documentChanged) { 

            int response = JOptionPane.showConfirmDialog(this, 

                "Current document has unsaved changes. Save changes?", 

                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION); 

             

            if (response == JOptionPane.YES_OPTION) { 

                if (!saveFile(false)) { 

                    return; // User cancelled save operation 

                } 

            } else if (response == JOptionPane.CANCEL_OPTION) { 

                return; // User cancelled operation 

            } 

        } 

         

        int returnVal = fileChooser.showOpenDialog(this); 

         

        if (returnVal == JFileChooser.APPROVE_OPTION) { 

            File file = fileChooser.getSelectedFile(); 

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) { 

                textArea.setText(""); 

                String line; 

                while ((line = reader.readLine()) != null) { 

                    textArea.append(line + "\n"); 

                } 

                 

                currentFile = file; 

                undoStack.clear(); 

                redoStack.clear(); 

                editHistory.clear(); 

                String text = textArea.getText(); 

                undoStack.push(text); 

                editHistory.addEdit(text); 

                documentChanged = false; 

                updateStatusBar(); 

                updateTitle(); 

                 

                statusLabel.setText("File opened: " + file.getName()); 

            } catch (IOException e) { 

                JOptionPane.showMessageDialog(this, 

                    "Error opening file: " + e.getMessage(), 

                    "Error", JOptionPane.ERROR_MESSAGE); 

            } 

        } 

    } 

     

    private boolean saveFile(boolean saveAs) { 

        if (currentFile == null || saveAs) { 

            int returnVal = fileChooser.showSaveDialog(this); 

             

            if (returnVal == JFileChooser.APPROVE_OPTION) { 

                File file = fileChooser.getSelectedFile(); 

                 

                // Add .txt extension if no extension is provided 

                if (!file.getName().contains(".")) { 

                    file = new File(file.getAbsolutePath() + ".txt"); 

                } 

                 

                // Confirm overwrite if file exists 

                if (file.exists() && !file.equals(currentFile)) { 

                    int response = JOptionPane.showConfirmDialog(this, 

                        "The file exists, do you want to overwrite?", 

                        "Confirm Overwrite", JOptionPane.YES_NO_OPTION); 

                     

                    if (response != JOptionPane.YES_OPTION) { 

                        return false; // User cancelled overwrite 

                    } 

                } 

                 

                currentFile = file; 

            } else { 

                return false; // User cancelled save dialog 

            } 

        } 

         

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentFile))) { 

            writer.write(textArea.getText()); 

            documentChanged = false; 

            updateTitle(); 

            statusLabel.setText("File saved: " + currentFile.getName()); 

            return true; 

        } catch (IOException e) { 

            JOptionPane.showMessageDialog(this, 

                "Error saving file: " + e.getMessage(), 

                "Error", JOptionPane.ERROR_MESSAGE); 

            return false; 

        } 

    } 

     

    private void exitApplication() { 

        if (documentChanged) { 

            int response = JOptionPane.showConfirmDialog(this, 

                "Current document has unsaved changes. Save changes?", 

                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION); 

             

            if (response == JOptionPane.YES_OPTION) { 

                if (!saveFile(false)) { 

                    return; // User cancelled save operation 

                } 

            } else if (response == JOptionPane.CANCEL_OPTION) { 

                return; // User cancelled operation 

            } 

        } 

         

        System.exit(0); 

    } 

     

    private void loadDictionary() { 

        // Java keywords 

        String[] javaKeywords = { 

            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", 

            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", 

            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", 

            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", 

            "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", 

            "true", "false", "null" 

        }; 

         

        // Common Java classes 

        String[] javaClasses = { 

            "String", "Integer", "Boolean", "Character", "Byte", "Short", "Long", "Float", "Double", 

            "Math", "System", "Object", "Class", "Thread", "Runnable", "Exception", "RuntimeException", 

            "Throwable", "Error", "ArrayList", "LinkedList", "HashMap", "HashSet", "TreeMap", "TreeSet", 

            "Vector", "Stack", "Queue", "Deque", "PriorityQueue", "Collections", "Arrays", "List", "Set", 

            "Map", "Iterator", "Iterable", "Comparable", "Comparator", "StringBuilder", "StringBuffer", 

            "Scanner", "File", "FileReader", "FileWriter", "BufferedReader", "BufferedWriter", "PrintWriter", 

            "InputStream", "OutputStream", "Reader", "Writer", "Socket", "ServerSocket", "URL", "URLConnection", 

            "Date", "Calendar", "LocalDate", "LocalTime", "LocalDateTime", "ZonedDateTime", "Instant", 

            "Optional", "Stream", "Collector", "Collectors", "Function", "Predicate", "Consumer", "Supplier", 

            "BiFunction", "BiPredicate", "BiConsumer", "Enum", "Annotation", "Override", "Deprecated", 

            "SuppressWarnings", "FunctionalInterface" 

        }; 

         

        // Java methods and coding patterns 

        String[] javaMethods = { 

            "main", "toString", "equals", "hashCode", "compareTo", "clone", "valueOf", "length", "size", 

            "isEmpty", "contains", "add", "remove", "clear", "get", "set", "put", "charAt", "substring", 

            "indexOf", "lastIndexOf", "toUpperCase", "toLowerCase", "trim", "split", "replace", "replaceAll", 

            "matches", "format", "printf", "println", "print", "append", "delete", "insert", "reverse", 

            "next", "hasNext", "nextLine", "hasNextLine", "close", "flush", "read", "write", "execute", 

            "start", "stop", "run", "wait", "notify", "notifyAll", "sleep", "join", "interrupt", "isAlive", 

            "compile", "find", "group", "matcher", "pattern", "parse", "format", "sort", "binarySearch", 

            "fill", "copy", "asList", "toArray", "forEach", "filter", "map", "reduce", "collect", "of", 

            "getClass", "getName", "newInstance", "forName", "getDeclaredMethods", "getMethod", "invoke", 

            "isInstance", "cast", "asSubclass", "getConstructor", "newInstance" 

        }; 

         

        // Java UI components (Swing/JavaFX) 

        String[] javaUI = { 

            "JFrame", "JPanel", "JButton", "JLabel", "JTextField", "JTextArea", "JScrollPane", "JMenuBar", 

            "JMenu", "JMenuItem", "JCheckBox", "JRadioButton", "ButtonGroup", "JComboBox", "JList", 

            "JTable", "JTree", "JSplitPane", "JTabbedPane", "JDialog", "JOptionPane", "Border", "BorderFactory", 

            "GridLayout", "BorderLayout", "FlowLayout", "CardLayout", "BoxLayout", "GridBagLayout", 

            "GroupLayout", "SpringLayout", "Font", "Color", "Dimension", "Point", "Rectangle", "ActionListener", 

            "ActionEvent", "MouseListener", "MouseEvent", "KeyListener", "KeyEvent", "ItemListener", 

            "WindowListener", "FocusListener", "ChangeListener", "DocumentListener", "Scene", "Stage", 

            "Application", "Button", "Label", "TextField", "TextArea", "ComboBox", "ListView", "TableView", 

            "TreeView", "ScrollPane", "TabPane", "BorderPane", "GridPane", "FlowPane", "AnchorPane", 

            "HBox", "VBox", "MenuItem", "MenuBar", "Dialog", "Alert", "Timeline", "Animation" 

        }; 

         

        // Java coding conventions and patterns 

        String[] javaPatterns = { 

            "getter", "setter", "constructor", "singleton", "factory", "builder", "adapter", "observer", 

            "decorator", "strategy", "command", "proxy", "composite", "iterator", "state", "template", 

            "visitor", "mediator", "memento", "prototype", "facade", "flyweight", "bridge", "interpreter", 

            "repository", "service", "controller", "model", "view", "dao", "dto", "pojo", "bean", "entity", 

            "dependency", "injection", "autowired", "component", "repository", "service", "controller", 

            "configuration", "bean", "transactional", "scheduled", "async", "lazy", "scope", "primary", 

            "qualifier", "profile", "conditional", "property", "value", "required", "validated", "valid", 

            "notnull", "nullable", "override", "implements", "extends", "throws", "try", "catch", "finally", 

            "synchronize", "volatile", "atomic", "concurrent", "thread", "runnable", "callable", "future" 

        }; 

         

        // Java file extensions and common terms 

        String[] javaTerms = { 

            "java", "class", "jar", "war", "maven", "gradle", "pom", "build", "junit", "test", "assert", 

            "mockito", "mock", "spring", "hibernate", "jpa", "jdbc", "servlet", "jsp", "jstl", "jsf", 

            "ejb", "jms", "jmx", "jndi", "soap", "rest", "api", "json", "xml", "yaml", "properties", 

            "logging", "log4j", "logback", "slf4j", "javadoc", "annotation", "reflection", "introspection", 

            "serialization", "deserialization", "bytecode", "classloader", "jvm", "jre", "jdk", "javac", 

            "javap", "jar", "jdeps", "jcmd", "jconsole", "jmap", "jstack", "jstat", "jvisualvm", "jshell" 

        }; 

         

        // Add all words to the dictionary 

        for (String word : javaKeywords) { 

            dictionary.insert(word); 

        } 

        for (String word : javaClasses) { 

            dictionary.insert(word); 

        } 

        for (String word : javaMethods) { 

            dictionary.insert(word); 

        } 

        for (String word : javaUI) { 

            dictionary.insert(word); 

        } 

        for (String word : javaPatterns) { 

            dictionary.insert(word); 

        } 

        for (String word : javaTerms) { 

            dictionary.insert(word); 

        } 

         

        // Also add common programming operators and symbols 

        String[] commonOperators = { 

            "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=", 

            "==", "!=", ">=", "<=", "&&", "||", "!", "&", "|", "^", "~", "<<", ">>", ">>>", 

            "+", "-", "*", "/", "%", "<", ">" 

        }; 

         

        for (String op : commonOperators) { 

            dictionary.insert(op); 

        } 

         

        // Common Java code snippets (as keywords) 

        String[] codeSnippets = { 

            "public", "static", "void", "main", "args", "System.out.println", "public class", 

            "private final", "extends", "implements", "try catch", "throws Exception",  

            "return null", "return true", "return false", "import java.util", "import java.io", 

            "import javax.swing", "import java.awt", "import java.net", "import java.sql" 

        }; 

         

        for (String snippet : codeSnippets) { 

            dictionary.insert(snippet); 

        } 

    } 

     

    public static void main(String[] args) { 

        try { 

            // Set system look and feel 

            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); 

        } catch (Exception e) { 

            e.printStackTrace(); 

        } 

         

        SwingUtilities.invokeLater(() -> new SmartTextEditor()); 

    } 

     

    // Inner class for Trie implementation 

    private class Trie { 

        private TrieNode root; 

         

        public Trie() { 

            root = new TrieNode(); 

        } 

         

        public void insert(String word) { 

            TrieNode current = root; 

             

            for (char c : word.toCharArray()) { 

                current.children.putIfAbsent(c, new TrieNode()); 

                current = current.children.get(c); 

            } 

             

            current.isEndOfWord = true; 

        } 

         

        public List<String> getSuggestions(String prefix) { 

            List<String> suggestions = new ArrayList<>(); 

            TrieNode current = root; 

             

            // Navigate to the last node of the prefix 

            for (char c : prefix.toCharArray()) { 

                if (!current.children.containsKey(c)) { 

                    return suggestions; // Prefix not found 

                } 

                current = current.children.get(c); 

            } 

             

            // Find all words starting with this prefix 

            findAllWordsFromNode(current, prefix, suggestions); 

             

            return suggestions; 

        } 

         

        private void findAllWordsFromNode(TrieNode node, String prefix, List<String> suggestions) { 

            if (suggestions.size() >= 10) { 

                return; // Limit to 10 suggestions 

            } 

             

            if (node.isEndOfWord) { 

                suggestions.add(prefix); 

            } 

             

            for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) { 

                findAllWordsFromNode(entry.getValue(), prefix + entry.getKey(), suggestions); 

            } 

        } 

         

        private class TrieNode { 

            Map<Character, TrieNode> children; 

            boolean isEndOfWord; 

             

            public TrieNode() { 

                children = new HashMap<>(); 

                isEndOfWord = false; 

            } 

        } 

    } 

     

    // Inner class for Edit History (Linked List) 

    private class EditHistory { 

        private ListNode head; 

        private ListNode tail; 

        private ListNode current; 

        private int size; 

         

        public EditHistory() { 

            head = null; 

            tail = null; 

            current = null; 

            size = 0; 

        } 

         

        public void addEdit(String text) { 

            ListNode newNode = new ListNode(text); 

             

            if (head == null) { 

                head = newNode; 

                tail = newNode; 

            } else { 

                tail.next = newNode; 

                newNode.prev = tail; 

                tail = newNode; 

} 

             

            current = newNode; 

            size++; 

        } 

         

        public String getPreviousEdit(String currentText) { 

            if (current == null || current.prev == null) { 

                return null; 

            } 

             

            // If the current text doesn't match the current node's text, 

            // find the matching node first 

            if (!current.text.equals(currentText)) { 

                ListNode temp = head; 

                while (temp != null) { 

                    if (temp.text.equals(currentText)) { 

                        current = temp; 

                        break; 

                    } 

                    temp = temp.next; 

                } 

            } 

             

            if (current.prev != null) { 

                current = current.prev; 

                return current.text; 

            } 

             

            return null; 

        } 

         

        public String getNextEdit(String currentText) { 

            if (current == null || current.next == null) { 

                return null; 

            } 

             

            // If the current text doesn't match the current node's text, 

            // find the matching node first 

            if (!current.text.equals(currentText)) { 

                ListNode temp = head; 

                while (temp != null) { 

                    if (temp.text.equals(currentText)) { 

                        current = temp; 

                        break; 

                    } 

                    temp = temp.next; 

                } 

            } 

             

            if (current.next != null) { 

                current = current.next; 

                return current.text; 

            } 

             

            return null; 

        } 

         

        public void clear() { 

            head = null; 

            tail = null; 

            current = null; 

            size = 0; 

        } 

         

        public int size() { 

            return size; 

        } 

         

        public int getCurrentPosition() { 

            if (current == null) { 

                return -1; 

            } 

             

            int position = 0; 

            ListNode temp = head; 

            while (temp != current && temp != null) { 

                position++; 

                temp = temp.next; 

            } 

             

            return temp == null ? -1 : position; 

        } 

         

        private class ListNode { 

            String text; 

            ListNode prev; 

            ListNode next; 

             

            public ListNode(String text) { 

                this.text = text; 

                this.prev = null; 

                this.next = null; 

            } 

        } 

    } 

} 