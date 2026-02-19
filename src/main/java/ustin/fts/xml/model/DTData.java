package ustin.fts.xml.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DTData {
    private String originCountryCode;
    private String decisionCode;
    private String decisionDate;
    private String prDocumentNumber;
    private String prDocumentDate;
}
