# Refactoring vs Greenfield: Strategic Analysis

## Executive Summary

**Short Answer:** For Runary, **strategic refactoring is better than pure greenfield** because:
- The Angular app has 404 files and 78 services with complex state management
- Risk of breaking critical features (readers, WebSocket) is high
- Users depend on the current functionality
- Gradual migration allows learning and validation

**However**, a **hybrid approach** is ideal: Build the new architecture in parallel while running the Angular app, then switch once feature parity is achieved.

## Detailed Comparison

### 1. Current State Assessment

**Runary Complexity:**
- 404 TypeScript files
- 78 services (state management, API calls)
- 57 backend controllers
- 13 feature modules
- Complex WebSocket integration (10+ topics)
- 4 different book readers (PDF, EPUB, CBX, Audiobook)
- Multi-format support, metadata fetching, real-time sync

**Key Challenge Areas:**
1. **Readers**: PDF.js, Foliate.js (EPUB), custom CBX viewer
2. **Real-time**: STOMP over WebSocket for progress updates
3. **State Management**: Complex RxJS streams, BehaviorSubjects
4. **Authentication**: JWT + OIDC/OAuth2 flows

### 2. Refactoring Approach Analysis

#### Pros ✅

**Lower Risk**
- Angular app keeps running during transition
- Rollback possible at any point
- Users never experience downtime
- Can validate each feature before switching

**Incremental Value Delivery**
- Ship improvements continuously
- Get user feedback early
- Fix issues in small increments
- Business continues as usual

**Knowledge Preservation**
- Transfer domain logic incrementally
- Team learns Kotlin/http4k gradually
- Existing tests provide safety net
- Understanding deepens through migration

**Feature Completeness**
- Won't miss edge cases
- Complex features migrated carefully
- Readers keep working during transition
- No "big bang" deployment risk

#### Cons ❌

**Higher Complexity**
- Two systems running simultaneously
- Proxy/configuration management
- Routing logic becomes complex
- Testing matrix doubles

**Longer Timeline**
- Coordination overhead
- Context switching between codebases
- Feature flags add complexity
- Total effort may be higher

**Technical Debt**
- Temporary bridges and adapters
- Feature flags to clean up later
- Dual maintenance period
- Possible inconsistency in UX

**Team Cognitive Load**
- Working in two tech stacks
- Context switching overhead
- More complex debugging
- Onboarding complexity

### 3. Greenfield Approach Analysis

#### Pros ✅

**Clean Slate**
- No legacy constraints
- Optimal architecture from start
- No technical debt
- Consistent patterns throughout

**Faster Development**
- No need to maintain Angular code
- Focus purely on new stack
- No compatibility concerns
- Simpler mental model

**Shorter Timeline**
- Parallel development possible
- No gradual migration overhead
- Single codebase to maintain
- Clear "switch over" moment

**Better Architecture**
- HTMX patterns from day one
- Proper hypermedia design
- No SPA baggage
- Progressive enhancement built-in

#### Cons ❌

**High Risk**
- All-or-nothing deployment
- No rollback option
- Complex features may break
- User impact if issues arise

**Feature Parity Challenge**
- Must rebuild ALL features
- Easy to miss edge cases
- Complex readers need full rewrite
- Testing burden is massive

**Knowledge Gap**
- Team learns new stack under pressure
- No gradual skill building
- Domain knowledge transfer is rushed
- Higher chance of mistakes

**Extended Parallel Running**
- Angular app needs maintenance
- Bug fixes in two places
- Feature parity takes time
- Double work during development

### 4. Comparison Matrix

| Factor | Refactoring | Greenfield | Winner |
|--------|-------------|------------|---------|
| **Risk** | Low (rollback possible) | High (all-or-nothing) | 🏆 Refactoring |
| **Timeline** | 20-24 weeks | 16-20 weeks | 🏆 Greenfield |
| **Complexity** | High (two systems) | Medium (one system) | 🏆 Greenfield |
| **Feature Parity** | Guaranteed | Risky | 🏆 Refactoring |
| **Learning Curve** | Gradual | Steep | 🏆 Refactoring |
| **Initial Investment** | Lower | Higher | 🏆 Refactoring |
| **Long-term Maintenance** | Medium | Lower | 🏆 Greenfield |
| **User Impact** | Minimal | Potential disruption | 🏆 Refactoring |
| **Team Stress** | Lower | Higher | 🏆 Refactoring |
| **Code Quality** | Mixed (transitional) | High | 🏆 Greenfield |

### 5. Specific Runary Considerations

#### Why Refactoring is Safer for Runary:

1. **Complex Readers**
   - PDF viewer with annotations
   - EPUB reader (Foliate.js integration)
   - CBX comic reader
   - Audiobook player with progress tracking
   - Migrating these incrementally is safer

2. **Real-time Features**
   - Library scanning progress
   - Metadata fetching updates
   - Book import notifications
   - WebSocket → SSE transition needs validation

3. **Data Integrity**
   - Reading progress across devices
   - User preferences
   - Book metadata
   - Risk of data loss during migration

4. **Authentication Complexity**
   - JWT token management
   - OIDC/OAuth2 integration
   - Session handling
   - Security is critical

#### When Greenfield Might Work:

1. **If starting a new product** (not applicable here)
2. **If current app is small** (404 files is not small)
3. **If no users yet** (Runary has users)
4. **If team is expert in new stack** (learning curve exists)
5. **If features are simple** (readers are complex)

### 6. Recommended Hybrid Approach

**"Parallel Build with Gradual Switch"**

```
Phase 1: Foundation (Weeks 1-4)
├── Set up http4k project
├── Build core infrastructure
├── Create basic book list
├── Run parallel to Angular
└── Proxy routes new → old

Phase 2: Core Features (Weeks 5-10)
├── Build authentication
├── Implement book browser
├── Add library management
├── Feature parity for basics
└── Test with internal users

Phase 3: Complex Features (Weeks 11-16)
├── Build metadata management
├── Implement search
├── Add readers (hybrid approach)
├── Migrate real-time updates
└── Beta testing with users

Phase 4: Switch Over (Week 17-18)
├── Route 100% to new app
├── Monitor closely
├── Fix critical issues
└── Decommission Angular

Phase 5: Cleanup (Week 19-20)
├── Remove feature flags
├── Optimize performance
├── Documentation
└── Team knowledge transfer
```

### 7. Decision Framework

**Choose Refactoring if:**
- ✅ You have active users who depend on the app
- ✅ Risk tolerance is low
- ✅ Team is learning new technology
- ✅ Complex features exist (readers, real-time)
- ✅ Need to deliver value incrementally
- ✅ Rollback capability is important

**Choose Greenfield if:**
- ✅ Starting from scratch (no existing users)
- ✅ Current app is simple (< 50 files)
- ✅ Team is expert in new stack
- ✅ Can tolerate downtime/bugs
- ✅ Budget allows parallel running
- ✅ Timeline is aggressive

### 8. Runary Recommendation

**For Runary specifically:**

**🏆 RECOMMEND: Strategic Refactoring with Parallel Development**

**Rationale:**
1. **User Base**: Runary is a self-hosted app with users who depend on it
2. **Complexity**: 404 files, complex readers, real-time features
3. **Learning**: Team transitioning from TypeScript/Angular to Kotlin/http4k
4. **Risk**: High risk of breaking readers or losing data
5. **Business**: Can't afford extended downtime or major bugs

**Modified Approach - "Safe Greenfield":**

```
┌──────────────────────────────────────────────────────────┐
│  Strategy: Build New, Migrate Gradually, Switch Once     │
└──────────────────────────────────────────────────────────┘

1. BUILD (Weeks 1-12)
   - Create http4k app in parallel
   - Don't touch Angular code
   - Build to feature parity
   - Run on separate port

2. VALIDATE (Weeks 13-14)
   - Internal testing
   - Beta users
   - Fix critical bugs
   - Performance testing

3. MIGRATE (Weeks 15-16)
   - Proxy traffic gradually (10% → 50% → 100%)
   - Monitor error rates
   - Rollback if issues

4. DECOMMISSION (Week 17-18)
   - Remove Angular
   - Clean up proxies
   - Documentation
```

**Why This Works:**
- ✅ Get benefits of greenfield (clean code, fast development)
- ✅ Get benefits of refactoring (low risk, rollback option)
- ✅ No "two systems" complexity in production
- ✅ Feature parity guaranteed before switch
- ✅ Users never see broken app

### 9. Effort Comparison

**Refactoring (20 weeks):**
- Setup: 2 weeks
- Incremental migration: 14 weeks
- Cleanup: 4 weeks
- Total effort: ~1000 hours

**Greenfield (16 weeks):**
- Setup: 2 weeks
- Development: 12 weeks
- Testing/deployment: 2 weeks
- Total effort: ~800 hours

**"Safe Greenfield" (18 weeks):**
- Setup: 2 weeks
- Parallel development: 10 weeks
- Testing: 2 weeks
- Gradual rollout: 2 weeks
- Cleanup: 2 weeks
- Total effort: ~900 hours

**Winner: "Safe Greenfield"** - Best balance of speed and safety.

### 10. Risk Mitigation

**Regardless of approach:**

1. **Keep Angular App Deployable**
   - Don't modify existing code
   - Keep build pipeline working
   - Can rollback instantly

2. **Database Compatibility**
   - New app uses same database
   - No data migration needed
   - Share backend API

3. **Feature Flags**
   - Route traffic gradually
   - A/B testing capability
   - Instant rollback

4. **Monitoring**
   - Error tracking from day one
   - Performance metrics
   - User analytics

5. **Rollback Plan**
   - Documented procedure
   - Tested rollback
   - < 5 min to revert

## Final Recommendation

**For Runary: Use "Safe Greenfield" Strategy**

**Steps:**
1. Build http4k + HTMX app in parallel (don't touch Angular)
2. Run on separate port (e.g., `:8080` while Angular runs on `:4200`)
3. Use nginx to route traffic (gradual or instant)
4. Achieve feature parity before switching
5. Switch over once confident (with rollback option)
6. Decommission Angular

**Why:**
- Faster than incremental refactoring
- Safer than pure greenfield
- Clean architecture from start
- Low risk with rollback capability
- Better for team learning

**Timeline: 18-20 weeks**

This gives you the best of both worlds: clean new architecture with minimal risk.
