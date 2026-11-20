package team3.freelance.domain.model;

/** FileStorage에 저장한 파일 한 건의 정보. Attachment를 만들 때 쓴다. */
public record FileInfo(String fileName, String format, long size, String storageKey) {

    /** 파일명에서 형식(확장자, 소문자)을 뽑는다. 확장자가 없으면 빈 문자열. */
    public static String formatOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
