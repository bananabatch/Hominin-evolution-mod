package dev.hominin.evolution.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The names a line carries. When a species gives way to the next, everything that band was
 * goes into the ground with it - except one name, which the descendants keep using because
 * somebody's grandmother used it, and nobody remembers why any more.
 *
 * <p>That is all inheritance is for most of the time it has existed: not property, not a
 * burial, a sound that outlasts the throat it came from.
 */
public class Ancestors {
    /** One remembered person: their name, and the species they walked as. */
    public record Ancestor(String name, String species) {
        public static final Codec<Ancestor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Ancestor::name),
                Codec.STRING.fieldOf("species").forGetter(Ancestor::species)
        ).apply(instance, Ancestor::new));
    }

    /** Ten deep is more genealogy than the mod has stages. */
    private static final int KEPT = 10;

    public static final Codec<Ancestors> CODEC = Ancestor.CODEC.listOf()
            .xmap(Ancestors::new, ancestors -> List.copyOf(ancestors.line));

    private final List<Ancestor> line;

    public Ancestors() {
        this(List.of());
    }

    public Ancestors(List<Ancestor> line) {
        this.line = new ArrayList<>(line);
    }

    public List<Ancestor> line() {
        return List.copyOf(line);
    }

    public void remember(String name, String species) {
        line.add(new Ancestor(name, species));
        while (line.size() > KEPT) {
            line.remove(0);
        }
    }

    @Nullable
    public Ancestor last() {
        return line.isEmpty() ? null : line.get(line.size() - 1);
    }

    public int size() {
        return line.size();
    }
}
