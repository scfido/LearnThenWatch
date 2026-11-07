import local.mytv.english.RemoteNavigation;
import local.mytv.english.RemoteNavigation.Screen;

public final class RemoteNavigationTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        RemoteNavigation nav = new RemoteNavigation(new Screen("home"));
        Screen map = new Screen("map"); nav.open(map); map.focus = "map:unit:40"; map.scroll = 240;
        Screen unit = new Screen("unit", "KET", 40); nav.open(unit); unit.focus = "unit:word:ket_library";
        nav.open(new Screen("word", "ket_library"));
        check(nav.back() == unit && nav.current().focus.equals("unit:word:ket_library"), "word returns to its exact unit and focused word");
        check(nav.back() == map && nav.current().scroll == 240, "unit returns to selected map position");
        nav.open(new Screen("records")); Screen review = new Screen("review"); nav.open(review);
        nav.open(new Screen("word", "ket_library")); check(nav.back() == review, "review word returns to review rather than map");
        nav.reset(new Screen("home")); nav.open(new Screen("pin", "unlock")); nav.replace(new Screen("parent"));
        check(nav.back().name.equals("home"), "completed PIN is not left in back history");
        nav.open(new Screen("parent")); nav.open(new Screen("confirm", "reset")); nav.open(new Screen("pin", "action-reset"));
        check(nav.back().name.equals("confirm"), "canceling a data PIN returns to the confirmation");
        nav.open(new Screen("pin", "action-reset")); check(nav.backTo("parent"), "finished data action can unwind to parent");
        check(nav.back().name.equals("home") && nav.back() == null, "back unwinds completely without navigation loops");
        nav.open(new Screen("map")); int count = nav.entries().size(); nav.open(new Screen("map"));
        check(nav.entries().size() == count, "same destination refresh never adds a back step");
        RemoteNavigation restored = new RemoteNavigation(new Screen("home")); restored.restore(nav.entries());
        check(restored.current().name.equals("map") && restored.back().name.equals("home"), "history can be restored");
        System.out.println("PASS: remote back history, entry points, focus/scroll, PIN replacement, confirmation unwind");
    }
}
