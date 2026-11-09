

## 1️⃣ Swift State Management

This skill focuses on **data flow, state ownership, and UI synchronization** in SwiftUI and iOS apps.

### Core Concepts

**State ownership & lifecycle**

* `@State` — local view state
* `@Binding` — two-way state sharing
* `@StateObject` — source of truth for reference types
* `@ObservedObject` — external observable model
* `@EnvironmentObject` — global dependency injection

**Reactive data flow**

* Combine framework basics (`Publisher`, `Subscriber`)
* `@Published` properties
* ObservableObject protocol
* SwiftUI view invalidation & diffing

**Architecture patterns**

* MVVM with SwiftUI
* Unidirectional data flow (Redux-style)
* Dependency injection & testability
* Handling async state with `async/await`

**Advanced topics**

* State normalization & avoiding duplication
* Derived state vs source of truth
* Performance considerations & view recomposition
* Managing side effects & network state

---

## 2️⃣ Swift HIG Design (Human Interface Guidelines)

This skill centers on designing iOS interfaces aligned with Apple’s usability standards.

### Core Principles

**Clarity**

* legible typography & SF Symbols
* visual hierarchy & spacing
* accessibility & dynamic type

**Deference**

* content-first UI
* avoiding visual noise
* proper use of translucency & blur

**Depth**

* layering & elevation
* motion feedback
* spatial relationships

### iOS UI Best Practices

* Navigation patterns (tab bar, stack navigation)
* Safe area & layout margins
* Touch target sizes (44pt minimum)
* Dark mode & adaptive colors
* Accessibility (VoiceOver, contrast, motion reduction)

### Platform Consistency

* System components before custom controls
* Native gestures & expected interactions
* Respect platform metaphors & conventions

---

## 3️⃣ Swift Animation Guide

This skill covers motion design & animation APIs for responsive and delightful UX.

![Image](https://www.createwithswift.com/content/images/2024/10/createwithswift.com-creating-view-transitions-in-swiftui-01-1.gif)

![Image](https://cdn.dribbble.com/userupload/17144881/file/still-583e3a27d9f8570cc2afad30f6735124.png?format=webp\&resize=400x300\&vertical=center)

![Image](https://www.createwithswift.com/content/images/2024/02/createwithswift.com-create-an-animated-transition-with-geometry-effect-in-swiftui-01.gif)

![Image](https://www.createwithswift.com/content/images/2024/02/createwithswift.com-create-an-animated-transition-with-geometry-effect-in-swiftui.gif)

### Animation Fundamentals

**Implicit animations**

```swift
withAnimation {
    isExpanded.toggle()
}
```

**Explicit animations**

* `.animation(.easeInOut, value:)`
* `Animation.spring()`
* timing curves & easing

### SwiftUI Animation APIs

* `transition()` (move, slide, opacity)
* `matchedGeometryEffect`
* `TimelineView` & keyframe animation
* gesture-driven animations
* animating layout changes

### Motion Design Principles

* meaningful motion vs decorative motion
* continuity & spatial awareness
* feedback & affordance
* duration & easing consistency

### Performance & Advanced Topics

* avoiding layout thrashing
* GPU vs CPU rendering costs
* animating large lists efficiently
* coordinating multi-step animations

---
