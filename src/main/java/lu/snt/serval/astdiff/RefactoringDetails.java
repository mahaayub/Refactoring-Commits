package lu.snt.serval.astdiff;

import org.json.JSONObject;

class RefactoringDetails {
    private String type;
    private String description;

    public void setType(String type) {
        this.type = type;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "RefactoringDetails{" +
                "type='" + type + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("description", description);
        return json;
    }
}
