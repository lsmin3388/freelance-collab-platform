package team3.freelance.domain.service;

/** 화면이 넘겨준 파일 한 건. Service가 Servlet API(MultipartFile)에 의존하지 않도록 바꿔 담는다. */
public record UploadedFile(String fileName, byte[] content) {
    public boolean isEmpty() {
        return fileName == null || fileName.isBlank() || content == null || content.length == 0;
    }
}
