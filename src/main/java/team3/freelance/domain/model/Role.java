package team3.freelance.domain.model;

/** 사용자 역할. 한 계정이 둘 다 가질 수 있다 (M_2, {overlapping}). */
public enum Role {
    CLIENT("의뢰자"), FREELANCER("프리랜서");

    private final String label;
    Role(String label) { this.label = label; }
    public String label() { return label; }
}
