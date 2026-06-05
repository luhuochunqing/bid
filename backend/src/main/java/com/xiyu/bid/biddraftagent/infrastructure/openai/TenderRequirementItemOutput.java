// Input: LLM JSON response for a single requirement item within a chunk
// Output: Mutable POJO – 7 requirement item fields (Jackson + jsonschema-generator compatible)
// Pos: biddraftagent/infrastructure/openai
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

public class TenderRequirementItemOutput {
    public String category;
    public String title;
    public String content;
    public boolean mandatory;
    public String sourceExcerpt;
    public Integer confidence;
    public String sectionPath;
}
