package burp;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.Deflater;
import javax.swing.*;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.util.Comparator;
public class ZlibHandler implements IBurpExtender, ITab, IHttpListener, IProxyListener {
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private JPanel mainPanel;
    private JTextArea hexInputArea;
    private JTextArea decompressedOutputArea;
    private JButton decompressButton;
    private JTable historyTable;
    private DefaultTableModel historyTableModel;
    private TableRowSorter<DefaultTableModel> tableSorter;
    private java.util.List<IHttpRequestResponse> historyData;
    private Set<String> historyHashes;
    private JCheckBox scopeFilterCheckBox;
    private JComboBox<String> encodingComboBox;
    private JCheckBox prettyPrintCheckBox;
    private JButton compressButton;
    private JCheckBox interceptCheckBox;
    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.historyData = new ArrayList<>();
        this.historyHashes = new HashSet<>();
        callbacks.setExtensionName("Zlib Handler");
        initializeUI();
        callbacks.registerHttpListener(this);
        callbacks.registerProxyListener(this);
        callbacks.addSuiteTab(this);
        callbacks.printOutput("Zlib Handler Extension loaded successfully");
    }
    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout());
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBorder(BorderFactory.createTitledBorder("History (Click to decompress)"));
        String[] columnNames = {"#", "Method", "URL", "Status", "Length", "Zlib Detected"};
        historyTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = new JTable(historyTableModel);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableSorter = new TableRowSorter<>(historyTableModel);
        tableSorter.setComparator(0, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                try {
                    int i1 = o1 instanceof Integer ? (Integer) o1 : Integer.parseInt(o1.toString());
                    int i2 = o2 instanceof Integer ? (Integer) o2 : Integer.parseInt(o2.toString());
                    return Integer.compare(i1, i2);
                } catch (NumberFormatException e) {
                    return o1.toString().compareTo(o2.toString());
                }
            }
        });
        historyTable.setRowSorter(tableSorter);
        tableSorter.setSortKeys(java.util.Arrays.asList(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = historyTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = historyTable.convertRowIndexToModel(selectedRow);
                    loadHistoryItem(modelRow);
                }
            }
        });
        JScrollPane historyScrollPane = new JScrollPane(historyTable);
        historyPanel.add(historyScrollPane, BorderLayout.CENTER);
        JPanel historyButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scopeFilterCheckBox = new JCheckBox("Show only in-scope URLs", false);
        scopeFilterCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshHistory();
            }
        });
        historyButtonPanel.add(scopeFilterCheckBox);
        JButton refreshButton = new JButton("Refresh History");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshHistory();
            }
        });
        historyButtonPanel.add(refreshButton);
        historyPanel.add(historyButtonPanel, BorderLayout.SOUTH);
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("HEX Input"));
        hexInputArea = new JTextArea(10, 50);
        hexInputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane hexScrollPane = new JScrollPane(hexInputArea);
        JPanel encodingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        encodingPanel.add(new JLabel("Encoding:"));
        String[] encodings = {"Auto-detect", "UTF-8", "EUC-KR", "CP949", "Windows-949", "ISO-8859-1"};
        encodingComboBox = new JComboBox<>(encodings);
        encodingComboBox.setSelectedIndex(0);
        encodingPanel.add(encodingComboBox);
        prettyPrintCheckBox = new JCheckBox("Pretty Print JSON", false);
        encodingPanel.add(prettyPrintCheckBox);
        decompressButton = new JButton("Decompress Zlib");
        decompressButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                decompressHexInput();
            }
        });
        encodingPanel.add(decompressButton);
        compressButton = new JButton("Compress to Zlib");
        compressButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                compressToZlib();
            }
        });
        encodingPanel.add(compressButton);
        inputPanel.add(hexScrollPane, BorderLayout.CENTER);
        inputPanel.add(encodingPanel, BorderLayout.SOUTH);
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(BorderFactory.createTitledBorder("Decompressed Output"));
        decompressedOutputArea = new JTextArea(10, 50);
        decompressedOutputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        decompressedOutputArea.setEditable(true);
        JScrollPane outputScrollPane = new JScrollPane(decompressedOutputArea);
        JPanel outputButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        interceptCheckBox = new JCheckBox("Intercept and auto-decompress Zlib responses", false);
        outputButtonPanel.add(interceptCheckBox);
        outputPanel.add(outputScrollPane, BorderLayout.CENTER);
        outputPanel.add(outputButtonPanel, BorderLayout.SOUTH);
        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputPanel, outputPanel);
        bottomSplitPane.setDividerLocation(300);
        bottomSplitPane.setResizeWeight(0.5);
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyPanel, bottomSplitPane);
        mainSplitPane.setDividerLocation(300);
        mainSplitPane.setResizeWeight(0.4);
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);
    }
    private void decompressHexInput() {
        String hexInput = hexInputArea.getText().trim();
        if (hexInput.isEmpty()) {
            decompressedOutputArea.setText("Please enter HEX data");
            return;
        }
        try {
            byte[] hexBytes = hexStringToByteArray(hexInput);
            int zlibStart = findZlibHeader(hexBytes);
            if (zlibStart == -1) {
                try {
                    String decompressed = decompressZlib(hexBytes);
                    String finalOutput = applyPrettyPrint(decompressed);
                    decompressedOutputArea.setText(finalOutput);
                    callbacks.printOutput("Successfully decompressed " + hexBytes.length + " bytes (no header found, used full data)");
                } catch (Exception e) {
                    decompressedOutputArea.setText("Error: Could not find Zlib header and decompression failed.\n" +
                                                  "Please ensure the HEX data contains valid Zlib compressed data.\n" +
                                                  "Error: " + e.getMessage());
                    callbacks.printError("Decompression error: " + e.getMessage());
                }
            } else {
                byte[] zlibData = Arrays.copyOfRange(hexBytes, zlibStart, hexBytes.length);
                String decompressed = decompressZlib(zlibData);
                String finalOutput = applyPrettyPrint(decompressed);
                decompressedOutputArea.setText("Zlib header found at offset " + zlibStart + " bytes\n\n" + finalOutput);
                callbacks.printOutput("Successfully decompressed " + zlibData.length + " bytes (header at offset " + zlibStart + ")");
            }
        } catch (Exception e) {
            decompressedOutputArea.setText("Error: " + e.getMessage());
            callbacks.printError("Decompression error: " + e.getMessage());
        }
    }
    private int findZlibHeader(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0x78) {
                int secondByte = data[i + 1] & 0xFF;
                if (secondByte == 0x9C || secondByte == 0x01 || secondByte == 0xDA ||
                    secondByte == 0x5E || secondByte == 0x9D || secondByte == 0xBB ||
                    secondByte == 0xFA || secondByte == 0x5C || secondByte == 0x1C ||
                    secondByte == 0x1D || secondByte == 0x1E || secondByte == 0x1F) {
                    return i;
                }
            }
        }
        return -1;
    }
    private void compressToZlib() {
        String input = decompressedOutputArea.getText().trim();
        if (input.isEmpty()) {
            callbacks.printError("No data to compress. Please decompress first or enter text.");
            return;
        }
        try {
            String selectedEncoding = (String) encodingComboBox.getSelectedItem();
            if (selectedEncoding == null || selectedEncoding.equals("Auto-detect")) {
                selectedEncoding = "UTF-8";
            }
            byte[] inputBytes = input.getBytes(selectedEncoding);
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
            deflater.setInput(inputBytes);
            deflater.finish();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            deflater.end();
            byte[] compressed = outputStream.toByteArray();
            String hexString = byteArrayToHexString(compressed);
            hexInputArea.setText(hexString);
            callbacks.printOutput("Compressed " + inputBytes.length + " bytes to " + compressed.length + " bytes (Zlib)");
            decompressedOutputArea.setText("Compressed data (HEX):\n" + hexString +
                                          "\n\nOriginal size: " + inputBytes.length + " bytes" +
                                          "\nCompressed size: " + compressed.length + " bytes" +
                                          "\nCompression ratio: " + String.format("%.2f%%", (1.0 - (double)compressed.length / inputBytes.length) * 100));
        } catch (Exception e) {
            callbacks.printError("Compression error: " + e.getMessage());
            decompressedOutputArea.setText("Error compressing: " + e.getMessage());
        }
    }
    private byte[] hexStringToByteArray(String hex) {
        hex = hex.replaceAll("\\s+", "").replaceAll(":", "").replaceAll("-", "");
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }
    private String decompressZlib(byte[] compressed) throws IOException, DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        inflater.end();
        byte[] decompressed = outputStream.toByteArray();
        String selectedEncoding = (String) encodingComboBox.getSelectedItem();
        if (selectedEncoding != null && !selectedEncoding.equals("Auto-detect")) {
            try {
                return new String(decompressed, selectedEncoding);
            } catch (Exception e) {
                return "Error decoding with " + selectedEncoding + ": " + e.getMessage() +
                       "\n\nBinary data (HEX):\n" + byteArrayToHexString(decompressed);
            }
        }
        String[] encodings = {"UTF-8", "EUC-KR", "CP949", "Windows-949", "ISO-8859-1", "US-ASCII"};
        String bestResult = null;
        int bestScore = -1;
        for (String encoding : encodings) {
            try {
                String decoded = new String(decompressed, encoding);
                int score = 0;
                boolean hasKorean = false;
                int controlChars = 0;
                int sampleSize = Math.min(decoded.length(), 1000);
                int step = decoded.length() > 1000 ? decoded.length() / 1000 : 1;
                for (int idx = 0; idx < sampleSize; idx += step) {
                    char c = decoded.charAt(idx);
                    if (c >= '\uAC00' && c <= '\uD7A3') {
                        hasKorean = true;
                        score += 10;
                    } else if (Character.isLetterOrDigit(c)) {
                        score += 2;
                    } else if (Character.isWhitespace(c)) {
                        score += 1;
                    } else if (c >= 32 && c <= 126) {
                        score += 1;
                    } else if (c >= 0x80 && c <= 0xFF) {
                        score += 1;
                    } else if (c >= 0x100 && c <= 0xFFFF) {
                        score += 2;
                    } else if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                        controlChars++;
                    }
                }
                if (decoded.length() > 1000) {
                    score = (int)(score * ((double)decoded.length() / sampleSize));
                }
                if (controlChars > decoded.length() * 0.1) {
                    score -= controlChars;
                }
                if (hasKorean) {
                    score += 50;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestResult = decoded;
                }
            } catch (Exception e) {
                continue;
            }
        }
        if (bestResult != null && bestScore > 0) {
            return bestResult;
        } else {
            return "Binary data (HEX):\n" + byteArrayToHexString(decompressed);
        }
    }
    private String byteArrayToHexString(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X ", b));
            if (hex.length() % 80 == 0) {
                hex.append("\n");
            }
        }
        return hex.toString();
    }
    private String applyPrettyPrint(String text) {
        if (!prettyPrintCheckBox.isSelected()) {
            return text;
        }
        try {
            return prettyPrintJSON(text);
        } catch (Exception e) {
            return text;
        }
    }
    private String prettyPrintJSON(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new IllegalArgumentException("Not a JSON object or array");
        }
        StringBuilder result = new StringBuilder();
        int indentLevel = 0;
        boolean inString = false;
        boolean escapeNext = false;
        String indent = "    ";
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escapeNext) {
                result.append(c);
                escapeNext = false;
                continue;
            }
            if (c == '\\') {
                result.append(c);
                escapeNext = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                result.append(c);
                continue;
            }
            if (inString) {
                result.append(c);
                continue;
            }
            switch (c) {
                case '{':
                case '[':
                    result.append(c);
                    indentLevel++;
                    if (i < trimmed.length() - 1 && trimmed.charAt(i + 1) != '}' && trimmed.charAt(i + 1) != ']') {
                        result.append('\n');
                        for (int j = 0; j < indentLevel; j++) {
                            result.append(indent);
                        }
                    }
                    break;
                case '}':
                case ']':
                    indentLevel--;
                    if (i > 0 && trimmed.charAt(i - 1) != '{' && trimmed.charAt(i - 1) != '[') {
                        result.append('\n');
                        for (int j = 0; j < indentLevel; j++) {
                            result.append(indent);
                        }
                    }
                    result.append(c);
                    break;
                case ',':
                    result.append(c);
                    if (i < trimmed.length() - 1 && trimmed.charAt(i + 1) != ' ' && trimmed.charAt(i + 1) != '\n') {
                        result.append('\n');
                        for (int j = 0; j < indentLevel; j++) {
                            result.append(indent);
                        }
                    }
                    break;
                case ':':
                    result.append(c).append(' ');
                    break;
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    break;
                default:
                    result.append(c);
                    break;
            }
        }
        return result.toString();
    }
    private boolean isZlibCompressed(byte[] data) {
        if (data.length < 2) {
            return false;
        }
        return (data[0] & 0xFF) == 0x78 &&
               ((data[1] & 0xFF) == 0x9C || (data[1] & 0xFF) == 0x01 ||
                (data[1] & 0xFF) == 0xDA || (data[1] & 0xFF) == 0x5E);
    }
    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        if (messageIsRequest) {
            return;
        }
        IResponseInfo responseInfo = helpers.analyzeResponse(messageInfo.getResponse());
        byte[] responseBody = Arrays.copyOfRange(
            messageInfo.getResponse(),
            responseInfo.getBodyOffset(),
            messageInfo.getResponse().length
        );
        if (isZlibCompressed(responseBody) || responseBody.length > 0) {
            SwingUtilities.invokeLater(() -> {
                addToHistory(messageInfo);
            });
        }
    }
    private void addToHistory(IHttpRequestResponse messageInfo) {
        try {
            IRequestInfo requestInfo = helpers.analyzeRequest(messageInfo);
            java.net.URL url = requestInfo.getUrl();
            if (scopeFilterCheckBox.isSelected() && !callbacks.isInScope(url)) {
                return;
            }
            IResponseInfo responseInfo = helpers.analyzeResponse(messageInfo.getResponse());
            byte[] responseBody = Arrays.copyOfRange(
                messageInfo.getResponse(),
                responseInfo.getBodyOffset(),
                messageInfo.getResponse().length
            );
            boolean isZlib = isZlibCompressed(responseBody);
            String hash = java.util.Arrays.hashCode(messageInfo.getRequest()) + ":" +
                         java.util.Arrays.hashCode(messageInfo.getResponse());
            if (!historyHashes.contains(hash)) {
                historyHashes.add(hash);
                historyData.add(messageInfo);
                int rowNum = historyTableModel.getRowCount();
                Object[] rowData = {
                    rowNum,
                    requestInfo.getMethod(),
                    url.toString(),
                    responseInfo.getStatusCode(),
                    responseBody.length,
                    isZlib ? "Yes" : "No"
                };
                historyTableModel.addRow(rowData);
                if (historyTableModel.getRowCount() % 10 == 0) {
                    tableSorter.setSortKeys(java.util.Arrays.asList(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
                }
            }
        } catch (Exception e) {
            callbacks.printError("Error adding to history: " + e.getMessage());
        }
    }
    private void refreshHistory() {
        historyTableModel.setRowCount(0);
        historyData.clear();
        historyHashes.clear();
        IHttpRequestResponse[] messages = callbacks.getProxyHistory();
        int rowNum = 0;
        boolean filterScope = scopeFilterCheckBox.isSelected();
        for (IHttpRequestResponse message : messages) {
            try {
                IRequestInfo requestInfo = helpers.analyzeRequest(message);
                java.net.URL url = requestInfo.getUrl();
                if (filterScope && !callbacks.isInScope(url)) {
                    continue;
                }
                IResponseInfo responseInfo = helpers.analyzeResponse(message.getResponse());
                byte[] responseBody = Arrays.copyOfRange(
                    message.getResponse(),
                    responseInfo.getBodyOffset(),
                    message.getResponse().length
                );
                boolean isZlib = isZlibCompressed(responseBody);
                String hash = java.util.Arrays.hashCode(message.getRequest()) + ":" +
                             java.util.Arrays.hashCode(message.getResponse());
                if (!historyHashes.contains(hash)) {
                    historyHashes.add(hash);
                    historyData.add(message);
                    Object[] rowData = {
                        rowNum++,
                        requestInfo.getMethod(),
                        url.toString(),
                        responseInfo.getStatusCode(),
                        responseBody.length,
                        isZlib ? "Yes" : "No"
                    };
                    historyTableModel.addRow(rowData);
                }
            } catch (Exception e) {
                callbacks.printError("Error processing history item: " + e.getMessage());
            }
        }
        if (rowNum > 0) {
            tableSorter.setSortKeys(java.util.Arrays.asList(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        }
        callbacks.printOutput("History refreshed: " + rowNum + " items" +
                            (filterScope ? " (in-scope only)" : ""));
    }
    private void loadHistoryItem(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < historyData.size()) {
            IHttpRequestResponse message = historyData.get(rowIndex);
            IResponseInfo responseInfo = helpers.analyzeResponse(message.getResponse());
            byte[] responseBody = Arrays.copyOfRange(
                message.getResponse(),
                responseInfo.getBodyOffset(),
                message.getResponse().length
            );
            String hexString = byteArrayToHexString(responseBody);
            hexInputArea.setText(hexString);
            int zlibStart = findZlibHeader(responseBody);
            if (zlibStart != -1 || isZlibCompressed(responseBody)) {
                try {
                    byte[] zlibData = responseBody;
                    if (zlibStart != -1) {
                        zlibData = Arrays.copyOfRange(responseBody, zlibStart, responseBody.length);
                    }
                    String decompressed = decompressZlib(zlibData);
                    String finalOutput = applyPrettyPrint(decompressed);
                    if (zlibStart != -1) {
                        decompressedOutputArea.setText("Zlib header found at offset " + zlibStart + " bytes\n\n" + finalOutput);
                    } else {
                        decompressedOutputArea.setText(finalOutput);
                    }
                    callbacks.printOutput("Auto-decompressed history item #" + rowIndex);
                } catch (Exception e) {
                    decompressedOutputArea.setText("Error decompressing: " + e.getMessage() +
                                                  "\n\nTrying to find Zlib header in the data...");
                    callbacks.printError("Decompression error: " + e.getMessage());
                }
            } else {
                decompressedOutputArea.setText("This response is not Zlib compressed.\n" +
                                              "Response body length: " + responseBody.length + " bytes\n\n" +
                                              "HEX data:\n" + hexString);
            }
        }
    }
    @Override
    public String getTabCaption() {
        return "Zlib Handler";
    }
    @Override
    public Component getUiComponent() {
        return mainPanel;
    }
    @Override
    public void processProxyMessage(boolean messageIsRequest, IInterceptedProxyMessage message) {
        if (!interceptCheckBox.isSelected() || messageIsRequest) {
            return;
        }
        IHttpRequestResponse messageInfo = message.getMessageInfo();
        byte[] response = messageInfo.getResponse();
        if (response == null || response.length == 0) {
            return;
        }
        try {
            IResponseInfo responseInfo = helpers.analyzeResponse(response);
            byte[] responseBody = Arrays.copyOfRange(
                response,
                responseInfo.getBodyOffset(),
                response.length
            );
            int zlibStart = findZlibHeader(responseBody);
            if (zlibStart != -1 || isZlibCompressed(responseBody)) {
                try {
                    byte[] zlibData = responseBody;
                    if (zlibStart != -1) {
                        zlibData = Arrays.copyOfRange(responseBody, zlibStart, responseBody.length);
                    }
                    String decompressed = decompressZlib(zlibData);
                    String finalOutput = applyPrettyPrint(decompressed);
                    String selectedEncoding = (String) encodingComboBox.getSelectedItem();
                    if (selectedEncoding == null || selectedEncoding.equals("Auto-detect")) {
                        selectedEncoding = "UTF-8";
                    }
                    byte[] decompressedBytes = finalOutput.getBytes(selectedEncoding);
                    byte[] newResponse = helpers.buildHttpMessage(
                        responseInfo.getHeaders(),
                        decompressedBytes
                    );
                    messageInfo.setResponse(newResponse);
                    callbacks.printOutput("Intercepted and decompressed Zlib response (" +
                                        responseBody.length + " -> " + decompressedBytes.length + " bytes)");
                } catch (Exception e) {
                    callbacks.printError("Error decompressing intercepted response: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            callbacks.printError("Error processing intercepted message: " + e.getMessage());
        }
    }
}
