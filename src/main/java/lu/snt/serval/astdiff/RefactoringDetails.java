package lu.snt.serval.astdiff;
import org.json.JSONObject;
public class RefactoringDetails {
    private String type;
    private String description;
    private int rightSideLineNumber;


    // Getters and setters for all fields
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getRightSideLineNumber() {
        return rightSideLineNumber;
    }

    public void setRightSideLineNumber(int rightSideLineNumber) {
        this.rightSideLineNumber = rightSideLineNumber;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("description", description);
        json.put("rightSideLineNumber", rightSideLineNumber);
        return json;
    }
}



