/**
 * Représente une clé de chiffrement composée de deux paramètres r et s.
 * Cette classe est utilisée pour stocker et manipuler les clés de chiffrement
 * utilisées dans les algorithmes de permutation de lignes.
 */
public class Key {
    /**
     * Le paramètre r de la clé (généralement compris entre 0 et 255).
     */
    public int r;

    /**
     * Le paramètre s de la clé (généralement compris entre 0 et 127).
     */
    public int s;

    /**
     * Construit une nouvelle clé avec les paramètres spécifiés.
     *
     * @param r le paramètre r de la clé
     * @param s le paramètre s de la clé
     */
    public Key(int r, int s) {
        this.r = r;
        this.s = s;
    }
    
    /**
     * Retourne le paramètre r de la clé.
     *
     * @return le paramètre r
     */
    public int getR() {
        return r;
    }
    
    /**
     * Retourne le paramètre s de la clé.
     *
     * @return le paramètre s
     */
    public int getS() {
        return s;
    }
    
    /**
     * Indique si cet objet est égal à un autre objet.
     * Deux clés sont considérées comme égales si leurs paramètres r et s sont égaux.
     *
     * @param o l'objet à comparer
     * @return true si les objets sont égaux, false sinon
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Key key = (Key) o;
        return r == key.r && s == key.s;
    }
    
    /**
     * Retourne le code de hachage de la clé.
     * Le code de hachage est calculé en multipliant le paramètre r par 31 et en y ajoutant le paramètre s.
     *
     * @return le code de hachage de la clé
     */
    @Override
    public int hashCode() {
        return 31 * r + s;
    }
    
    /**
     * Retourne une représentation textuelle de la clé.
     *
     * @return une chaîne au format "Key{r=valeur, s=valeur}"
     */
    @Override
    public String toString() {
        return "Key{r=" + r + ", s=" + s + "}";
    }
}
