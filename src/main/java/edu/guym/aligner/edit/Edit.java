package edu.guym.aligner.edit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Edit<T> implements Comparable<Edit<T>> {

    private final Segment<T> source;
    private final Segment<T> target;

    protected Edit(Segment<T> source,
                   Segment<T> target) {
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
    }

    public static <T> Edit<T> of(Operation operation,
                                 Segment<T> source,
                                 Segment<T> target) {
        return builder()
                .<T>ofType(operation)
                .source(source)
                .target(target)
                .build();
    }

    public static EditBuilder builder() {
        return new EditBuilder();
    }

    public abstract Operation operation();

    public abstract <R> R accept(EditVisitor<T, R> visitor);

    public final Segment<T> source() {
        return source;
    }

    public final Segment<T> target() {
        return target;
    }

    public final Stream<T> stream() {
        return Stream.concat(source().stream(), target().stream());
    }

    public final <E> Stream<E> streamSegments(Function<? super Segment<T>, ? extends E> s,
                                              Function<? super Segment<T>, ? extends E> t) {
        return Stream.concat(Stream.of(s.apply(source())), Stream.of(t.apply(target())));
    }

    public final boolean matches(Predicate<? super Edit<T>> predicate) {
        return predicate.test(this);
    }

    public final <R> R transform(Function<? super Edit<T>, ? extends R> mapper) {
        return mapper.apply(this);
    }

    public <E> Edit<E> map(Function<? super T, ? extends E> mapper) {
        return of(operation(), source().map(mapper), target().map(mapper));
    }

    public <E> Edit<E> mapSegments(Function<Segment<T>, Segment<E>> sourceMapper,
                                   Function<Segment<T>, Segment<E>> targetMapper) {
        return of(
                operation(),
                sourceMapper.apply(source()),
                targetMapper.apply(target())
        );
    }

    public Optional<? extends Edit<T>> filter(Predicate<? super Edit<T>> predicate) {
        return Optional.of(this).filter(predicate);
    }

    /**
     * Merges this edit with the supplied other, creating a new edit.
     * <p>
     * The edits must be adjacent for the merge to be successful.
     * <p>
     * To test adjacency we first sort the edits, and test if left {@link #isLeftSiblingOf(Edit)} right. if the test fails an exception is thrown.
     * <p>
     * The merged edit will contain all elements but may have a different operation.
     * If both edits are equal, this is returned.
     * <p>
     * This method is symmetrical, i.e. given editA and editB, editA.mergeWith(editB) equals editB.mergeWith(editA).
     * @param other
     * @return the merged edit
     */
    public final Edit<T> mergeWith(Edit<T> other) {
        Objects.requireNonNull(other);

        if (equals(other)) {
            return this;
        }

        List<Edit<T>> sorted = Stream.of(this, other)
                .sorted()
                .collect(Collectors.toList());
        Edit<T> left = sorted.get(0);
        Edit<T> right = sorted.get(1);

        if (!left.isLeftSiblingOf(right)) {
            throw new IllegalArgumentException("cannot merge edits");
        }

        Operation operation = mergeOperations(other.operation());
        Segment<T> source = left.source.append(right.source.tokens());
        Segment<T> target = left.target.append(right.target.tokens());

        return Edit.of(operation, source, target);
    }

    public final boolean isLeftSiblingOf(Edit<T> other) {
        Segment<T> leftSource = this.source;
        Segment<T> leftTarget = this.target;
        Segment<T> rightSource = other.source;
        Segment<T> rightTarget = other.target;
        return leftSource.position() + leftSource.size() == rightSource.position() &&
                leftTarget.position() + leftTarget.size() == rightTarget.position();
    }

    protected abstract Operation mergeOperations(Operation other);

    @Override
    public final int compareTo(Edit<T> o) {
        return EditComparator.INSTANCE.compare(this, o);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edit<?> that = (Edit<?>) o;
        return operation() == that.operation() &&
                Objects.equals(source, that.source) &&
                Objects.equals(target, that.target);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(operation(), source, target);
    }

}
