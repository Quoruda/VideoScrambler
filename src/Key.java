public class Key {
    public int r;
    public int s;
    
    public Key(int r, int s) {
        this.r = r;
        this.s = s;
    }
    
    public int getR() {
        return r;
    }
    
    public int getS() {
        return s;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Key key = (Key) o;
        return r == key.r && s == key.s;
    }
    
    @Override
    public int hashCode() {
        return 31 * r + s;
    }
    
    @Override
    public String toString() {
        return "Key{r=" + r + ", s=" + s + "}";
    }
}

