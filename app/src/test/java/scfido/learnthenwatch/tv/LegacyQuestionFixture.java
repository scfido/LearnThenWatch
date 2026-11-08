package scfido.learnthenwatch.tv;

import java.util.Random;

public final class LegacyQuestionFixture {
    // First choice in source is the answer; the displayed choices are shuffled.
    private static final String[][] BANK = {
        {"apple 的中文意思是？", "苹果", "香蕉", "橙子"},
        {"cat 的中文意思是？", "猫", "狗", "兔子"},
        {"book 的中文意思是？", "书", "笔", "桌子"},
        {"red 是哪一种颜色？", "红色", "蓝色", "绿色"},
        {"“谢谢”用英语怎么说？", "Thank you.", "Goodbye.", "Good night."},
        {"早上见面应该说什么？", "Good morning.", "Good night.", "Goodbye."},
        {"I ___ a student.", "am", "is", "are"},
        {"She ___ my friend.", "is", "am", "are"},
        {"“三”用英语怎么说？", "three", "two", "ten"},
        {"dog 的中文意思是？", "狗", "猫", "鸟"},
        {"blue 是哪一种颜色？", "蓝色", "红色", "黄色"},
        {"“牛奶”用英语怎么说？", "milk", "water", "juice"},
        {"“爸爸”用英语怎么说？", "father", "mother", "sister"},
        {"“星期一”用英语怎么说？", "Monday", "Sunday", "Friday"},
        {"We ___ happy.", "are", "is", "am"},
        {"“我喜欢苹果。”是哪一句？", "I like apples.", "I am an apple.", "This is a cat."},
        {"bird 的中文意思是？", "鸟", "鱼", "熊"},
        {"“眼睛”用英语怎么说？", "eye", "ear", "hand"},
        {"“七”用英语怎么说？", "seven", "six", "eight"},
        {"“生日快乐！”用英语怎么说？", "Happy birthday!", "Good morning!", "See you!"},
        {"“黄色”用英语怎么说？", "yellow", "green", "black"},
        {"“水”用英语怎么说？", "water", "bread", "rice"},
        {"“老师”用英语怎么说？", "teacher", "student", "doctor"},
        {"It is ___ orange.", "an", "a", "am"}
    };
    public final String prompt;
    public final String[] options = new String[3];
    public final int correct;
    public final int index;
    public LegacyQuestionFixture(Random random, int last) {
        int chosen;
        do { chosen = random.nextInt(BANK.length); } while (chosen == last);
        index = chosen;
        String[] row = BANK[chosen];
        prompt = row[0];
        int[] order = {0, 1, 2};
        for (int i = 2; i > 0; i--) {
            int j = random.nextInt(i + 1), v = order[i]; order[i] = order[j]; order[j] = v;
        }
        int answer = 0;
        for (int i = 0; i < 3; i++) { options[i] = row[order[i] + 1]; if (order[i] == 0) answer = i; }
        correct = answer;
    }
}
