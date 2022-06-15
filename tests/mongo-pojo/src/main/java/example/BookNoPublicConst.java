
package example;

import org.bson.types.ObjectId;

public class BookNoPublicConst {
    private ObjectId id;
    private String title;
    private int pages;

    public BookNoPublicConst(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}
