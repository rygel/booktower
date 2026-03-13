# BookLore Frontend Architecture

## Overview

BookLore's frontend is built with **Angular 21** using modern Angular features including standalone components, signals, and the new application builder. It follows a feature-based architecture with clear module boundaries.

## Technology Stack

### Core Framework
- **Angular 21.2.0** - Frontend framework
- **TypeScript 5.9.3** - Programming language
- **RxJS 7.8.2** - Reactive programming
- **Zone.js** - Change detection

### UI Components
- **PrimeNG 21.1.1** - UI component library
- **PrimeIcons 7.0.0** - Icon library
- **PrimeUIX Themes 2.0.3** - Theming system
- **Angular CDK** - Component development kit

### State Management
- **RxJS BehaviorSubjects** - Reactive state
- **Angular Services** - State containers
- **No NgRx** - Lightweight approach

### Communication
- **HTTP Client** - REST API communication
- **RxStomp** - WebSocket/STOMP client
- **Server-Sent Events** - For progress updates

### Internationalization
- **Transloco 8.2.1** - i18n library
- 15+ language support

### Reader Components
- **ngx-extended-pdf-viewer** - PDF reading
- **Foliate.js** (embedded) - EPUB reading
- **Custom CBX reader** - Comic book reading

### Additional Libraries
- **Chart.js 4.5.1** - Data visualization
- **ng2-charts** - Chart.js Angular wrapper
- **date-fns** - Date manipulation
- **DOMPurify** - XSS protection
- **Showdown** - Markdown rendering
- **UUID** - Unique identifiers
- **Tween.js** - Animations

## Directory Structure

```
booklore-ui/src/
├── app/
│   ├── app.component.ts           # Root component
│   ├── app.routes.ts              # Route definitions
│   ├── core/                      # Core services & config
│   │   ├── config/               # Configuration
│   │   ├── security/             # Auth, guards, interceptors
│   │   ├── services/             # Core services
│   │   └── testing/              # Testing utilities
│   ├── features/                  # Feature modules (13 features)
│   │   ├── book/                 # Book browsing & management
│   │   ├── readers/              # Book readers (PDF, EPUB, CBX)
│   │   ├── metadata/             # Metadata management
│   │   ├── settings/             # Application settings
│   │   ├── dashboard/            # Dashboard
│   │   ├── author-browser/       # Author browsing
│   │   ├── series-browser/       # Series browsing
│   │   ├── stats/                # Statistics
│   │   ├── bookdrop/             # File upload
│   │   ├── notebook/             # User notes
│   │   ├── magic-shelf/          # Smart shelves
│   │   └── library-creator/      # Library creation
│   ├── shared/                    # Shared code
│   │   ├── components/           # Reusable components
│   │   ├── layout/               # Layout components
│   │   ├── services/             # Shared services
│   │   ├── models/               # TypeScript models
│   │   ├── websocket/            # WebSocket utilities
│   │   └── util/                 # Utilities
│   └── ...
├── assets/                        # Static assets
├── i18n/                          # Translation files
└── environments/                  # Environment configs
```

## Architectural Patterns

### 1. Feature-Based Organization

Code is organized by feature rather than by technical layer:

```
features/book/
├── components/           # UI components
├── service/              # Feature services
├── model/                # Domain models
└── ...
```

### 2. Standalone Components

Angular 14+ standalone components used throughout:
```typescript
@Component({
  selector: 'app-book-browser',
  standalone: true,
  imports: [CommonModule, ButtonModule, ...],
  templateUrl: './book-browser.component.html'
})
export class BookBrowserComponent { }
```

### 3. Service-Based State Management

No global store; state managed via services with RxJS:

```typescript
@Injectable({ providedIn: 'root' })
export class BookStateService {
  private books$ = new BehaviorSubject<Book[]>([]);
  
  getBooks(): Observable<Book[]> {
    return this.books$.asObservable();
  }
  
  updateBooks(books: Book[]): void {
    this.books$.next(books);
  }
}
```

### 4. Lazy Loading

Features loaded on demand for better performance:
```typescript
{
  path: 'settings',
  loadComponent: () => import('./features/settings/settings.component')
    .then(m => m.SettingsComponent)
}
```

### 5. Dependency Injection

Modern Angular inject function used:
```typescript
export class BookComponent {
  private bookService = inject(BookService);
  private router = inject(Router);
}
```

## Key Components

### App Component
- Root component with WebSocket setup
- Global notification handling
- Online/offline detection
- Authentication initialization

### Routing
- Route guards for authentication (`AuthGuard`)
- Permission-based guards (`BookdropGuard`, `EditMetadataGuard`)
- Lazy-loaded feature routes
- 404 handling

### Layout
- `AppLayoutComponent` - Main application shell
- Sidebar navigation
- Responsive design with PrimeNG

### Readers
- **PDF Reader**: ngx-extended-pdf-viewer with annotations
- **EPUB Reader**: Embedded Foliate.js
- **CBX Reader**: Custom comic book reader
- **Audiobook Player**: Custom audio player

## State Management

### Reactive Patterns
- RxJS Observables for async operations
- BehaviorSubjects for shared state
- Async pipes in templates
- Subscription management with `takeUntil` or manual unsubscribe

### Service Architecture
- **Core services**: Authentication, HTTP interceptors
- **Feature services**: Domain-specific operations
- **Shared services**: Cross-cutting concerns (notifications, etc.)

## Communication

### HTTP API
- RESTful API consumption
- Error handling with interceptors
- Loading state management

### WebSocket
STOMP over WebSocket for real-time updates:
- Book additions/updates
- Task progress notifications
- Log messages

### Server-Sent Events
For metadata batch operations progress

## Security

### Authentication
- JWT token storage (localStorage)
- Token refresh mechanism
- OIDC/OAuth2 support
- Route guards

### Authorization
- Permission-based UI rendering
- API call guards
- Role-based access

### XSS Protection
- DOMPurify for HTML sanitization
- Angular's built-in sanitization

## Internationalization

- Transloco for translation management
- 15+ languages supported
- Runtime language switching
- Translation files in `i18n/`

## Build Configuration

### Angular CLI
- New application builder (`@angular/build`)
- Standalone component generation
- SCSS styling
- Production optimizations

### Bundle Optimization
- Lazy loading for features
- Tree shaking
- Budget limits (6MB max)
- Service worker for PWA features

### Assets
- PDF viewer assets
- Foliate.js EPUB reader
- Font files
- Images

## Testing Strategy

- **Vitest** - Unit testing (instead of Karma/Jasmine)
- **Angular Testing Utilities** - Component testing
- **Coverage** - v8 coverage reporting

## Notable Features

1. **Virtual Scrolling**: For large book lists (`ngx-virtual-scroller`)
2. **Lazy Image Loading**: Book covers with `ng-lazyload-image`
3. **Infinite Scroll**: For pagination
4. **Charts**: Reading statistics visualization
5. **Drag & Drop**: File uploads and organization
6. **PWA Support**: Service worker enabled
7. **Custom Fonts**: User font upload support
