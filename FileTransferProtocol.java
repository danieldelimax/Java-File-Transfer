import java.io.Serializable;

public class FileTransferProtocol {

    public static final int PORT = 12345;
    public static final String MSG_FILE_TYPE_LIST = "FILE_TYPE_LIST:";
    public static final String MSG_TEXT_PREFIX = "TEXT:";
    public static final String MSG_FILE_PREFIX = "FILE:";
    public static final String MSG_SERVER_SHUTDOWN = "SERVER_SHUTDOWN";
    public static final String MSG_SERVER_READY = "SERVER_READY";
    public static final String MSG_FILE_TYPE_ERROR = "FILE_TYPE_ERROR";

    // Classe para empacotar dados a serem enviados (texto ou arquivo)
    public static class TransferData implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type; // "TEXT" ou "FILE"
        private String fileName;
        private String extension;
        private String textMessage;
        private byte[] fileData;

        // Construtor para mensagem de texto
        public TransferData(String textMessage) {
            this.type = "TEXT";
            this.textMessage = textMessage;
        }

        // Construtor para arquivo
        public TransferData(String fileName, byte[] fileData) {
            this.type = "FILE";
            this.fileName = fileName;
            this.fileData = fileData;
            int dotIndex = fileName.lastIndexOf('.');
            this.extension = (dotIndex > 0) ? fileName.substring(dotIndex + 1).toLowerCase() : "";
        }

        // Getters
        public String getType() { return type; }
        public String getFileName() { return fileName; }
        public String getExtension() { return extension; }
        public String getTextMessage() { return textMessage; }
        public byte[] getFileData() { return fileData; }
        
        @Override
        public String toString() {
            return type.equals("TEXT") ? "Mensagem de Texto" : "Arquivo: " + fileName + " (." + extension + ")";
        }
    }
}