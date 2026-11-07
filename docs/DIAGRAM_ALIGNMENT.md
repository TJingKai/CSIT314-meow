# Sprint diagram alignment

## How to read this mapping

The scope is exactly the 36 completed stories below. `src/main/java/com/meow/boundary/`, `control/` and `entity/` contain the real named classes and methods. REST adapters invoke boundary methods; boundaries invoke the diagram controllers; controllers invoke entities. Traces are recorded at actual method entry, not copied from expected contracts. A user interaction spanning multiple requests is concatenated in the normal-path test. Dashed UML returns are data returns rather than repeated function invocations.

The web UI and Java presenter together implement the boundary: HTTP does not remotely execute a JavaScript class. Rendering methods construct view data/messages consumed by the browser. Security, request parsing, SQL helpers, session transport and image serving are supporting infrastructure and are not numbered diagram messages.

## Report conflicts and decisions

1. **#251:** the sequence diagram puts the `calculateTotalRow(rows)` self-call on `ViewReportTableController`; its BCE diagram puts the method on `ActivityReport`. Both genuine methods exist and share `ReportMath`. The runtime workflow calls the controller method, following the requested sequence priority.
2. **#224:** the sequence omits session creation (there is a numbering gap), while its BCE includes `LoginController.createSession()`. Login validates through `UserRepository.validateUser` before creating the session and displaying the result. This additional infrastructure call is exposed separately when tracing is enabled. The password is never placed in the session.
3. **#221:** BCE declares `User.saveUser` as void while the sequence labels a boolean return. Java uses the BCE signature; successful persistence makes the controller return true. Exceptions roll back the operation.
4. **#222:** the description refers to an existing account, while the sequence checks email uniqueness and creates a designer. The registration sequence is followed; #223 then sets pending verification and disables publishing. Tests assess these two consecutive episodes separately.
5. Designer lifecycle arguments retain diagram values (`PENDING_VERIFICATION`, `ACTIVE`, `REJECTED`); storage maps these to `pending`, `active`, `rejected`.
6. **#232/#240** use the same controller name with different operations. One `ViewCompanyProfileController` implements both operations and delegates to the specified `CompanyProfile` or `Company` entity respectively.
7. **#234:** `coverIndex` is zero-based over the existing gallery followed by newly supplied photos. `savePhotos` executes before `setCoverImage` inside one transaction. A failed cover choice rolls back the database changes.
8. **#249–251:** dates are Singapore dates; weekly ranges run Monday–Sunday and monthly ranges use calendar months. A selected future date is rejected. New IDPs are counted using their first `published_at`, consistent with the use-case description, rather than draft creation. Historical event totals survive subsequent unpublication.
9. The browser needs supplementary read-only form/list data and static file delivery. These are transport support endpoints, not extra user stories. Saving a favourite does not itself call the shortlist entity; the UI invokes #243 separately after #242.

## All 36 stories

| Sprint | Story | Use case | Boundary → controller | Sequence reference |
| --- | --- | --- | --- | --- |
| 1 | #220 | Fill Customer Registration Form | `RegistrationPage` →  | [Diagram](diagrams/story-220-sequence.png) |
| 1 | #221 | Create Customer Account | `RegistrationPage` → `RegistrationController` | [Diagram](diagrams/story-221-sequence.png) |
| 1 | #222 | Register Interior Designer Account | `DesignerRegistrationPage` → `DesignerRegistrationController` | [Diagram](diagrams/story-222-sequence.png) |
| 1 | #223 | Set Account Pending Verification | `VerificationPage` → `VerificationController` | [Diagram](diagrams/story-223-sequence.png) |
| 1 | #224 | Log In | `LoginPage` → `LoginController` | [Diagram](diagrams/story-224-sequence.png) |
| 1 | #225 | Log Out | `NavigationPage` → `LogoutController` | [Diagram](diagrams/story-225-sequence.png) |
| 1 | #226 | Role-Based Screens | `AppMenuView` → `AccessController` | [Diagram](diagrams/story-226-sequence.png) |
| 1 | #227 | Search Accounts | `AccountManagementPage` → `AccountSearchController` | [Diagram](diagrams/story-227-sequence.png) |
| 1 | #228 | Create Account for Any Role | `CreateAccountPage` → `CreateAccountController` | [Diagram](diagrams/story-228-sequence.png) |
| 1 | #229 | Suspend or Reactivate Account | `ManageAccountPage` → `AccountStatusController` | [Diagram](diagrams/story-229-sequence.png) |
| 1 | #230 | Delete Account | `ManageAccountPage` → `DeleteAccountController` | [Diagram](diagrams/story-230-sequence.png) |
| 1 | #256 | Approve or Reject Designer | `ReviewDesignerPage` → `DesignerApprovalController` | [Diagram](diagrams/story-256-sequence.png) |
| 1 | #231 | Edit Company Details | `CompanyProfilePage` → `CompanyProfileController` | [Diagram](diagrams/story-231-sequence.png) |
| 1 | #232 | Public Company Profile | `PublicCompanyProfilePage` → `ViewCompanyProfileController` | [Diagram](diagrams/story-232-sequence.png) |
| 2 | #233 | Create IDP | `CreateIDPPage` → `CreateIDPController` | [Diagram](diagrams/story-233-sequence.png) |
| 2 | #234 | Upload Photos and Choose Cover | `IDPPhotoPage` → `UploadPhotosController` | [Diagram](diagrams/story-234-sequence.png) |
| 2 | #235 | Edit IDP | `EditIDPPage` → `EditIDPController` | [Diagram](diagrams/story-235-sequence.png) |
| 2 | #236 | View Own IDP List | `MyProjectsPage` → `ViewMyIDPsController` | [Diagram](diagrams/story-236-sequence.png) |
| 2 | #237 | Search Published IDPs | `SearchPage` → `SearchIDPController` | [Diagram](diagrams/story-237-sequence.png) |
| 2 | #238 | View Search Results | `SearchResultsPage` → `ViewSearchResultsController` | [Diagram](diagrams/story-238-sequence.png) |
| 2 | #239 | View IDP Details and Gallery | `IDPDetailPage` → `ViewIDPDetailsController` | [Diagram](diagrams/story-239-sequence.png) |
| 2 | #240 | Company Link from IDP | `CompanyProfilePage` → `ViewCompanyProfileController` | [Diagram](diagrams/story-240-sequence.png) |
| 2 | #242 | Save Favourite without Duplicates | `IDPDetailPage` → `SaveFavouriteController` | [Diagram](diagrams/story-242-sequence.png) |
| 2 | #263 | Publish or Unpublish IDP | `MyIDPPage` → `PublishIDPController` | [Diagram](diagrams/story-263-sequence.png) |
| 2 | #266 | Assign IDP Categories | `IDPCategoryPage` → `AssignCategoriesController` | [Diagram](diagrams/story-266-sequence.png) |
| 2 | #273 | View Favourites List | `FavouritesPage` → `ViewFavouritesController` | [Diagram](diagrams/story-273-sequence.png) |
| 3 | #241 | Record IDP View | `IDPDetailPage` → `RecordViewController` | [Diagram](diagrams/story-241-sequence.png) |
| 3 | #243 | Record IDP Shortlist | `IDPDetailPage` → `RecordShortlistController` | [Diagram](diagrams/story-243-sequence.png) |
| 3 | #244 | Portfolio Totals | `DesignerDashboardPage` → `ViewTotalStatsController` | [Diagram](diagrams/story-244-sequence.png) |
| 3 | #245 | Stats per IDP | `DesignerDashboardPage` → `ViewIDPStatsController` | [Diagram](diagrams/story-245-sequence.png) |
| 3 | #246 | Create Category | `CategoryManagementPage` → `CreateCategoryController` | [Diagram](diagrams/story-246-sequence.png) |
| 3 | #247 | Edit Category | `CategoryManagementPage` → `EditCategoryController` | [Diagram](diagrams/story-247-sequence.png) |
| 3 | #248 | Make Category Available | `CategoryManagementPage` → `CategoryAvailabilityController` | [Diagram](diagrams/story-248-sequence.png) |
| 3 | #249 | Select Report Period | `ReportPage` → `SelectPeriodController` | [Diagram](diagrams/story-249-sequence.png) |
| 3 | #250 | Generate Activity Totals | `ReportPage` → `GenerateReportController` | [Diagram](diagrams/story-250-sequence.png) |
| 3 | #251 | View Daily Report Table | `ReportPage` → `ViewReportTableController` | [Diagram](diagrams/story-251-sequence.png) |

## Exact normal call orders

These lists are transcribed from the report and are the independent integration-test expectations. The actual Java methods record execution when called.

### #220 — Fill Customer Registration Form

```text
1. RegistrationPage.navigateToRegister
2. RegistrationPage.displayForm
3. RegistrationPage.enterDetails
4. FormValidator.validate
5. RegistrationForm.setDetails
6. RegistrationForm.isValid
7. RegistrationPage.enableSubmit
```

### #221 — Create Customer Account

```text
1. RegistrationPage.clickSubmit
2. RegistrationController.createAccount
3. User.isEmailRegistered
4. RegistrationController.hashPassword
5. User.saveUser
6. RegistrationPage.displaySuccessMsg
```

### #222 — Register Interior Designer Account

```text
1. DesignerRegistrationPage.navigateToDesignerRegister
2. DesignerRegistrationPage.displayForm
3. DesignerRegistrationPage.enterDetailsAndUpload
4. DesignerRegistrationPage.clickSubmitApplication
5. DesignerRegistrationController.submitApplication
6. InteriorDesigner.isEmailRegistered
7. InteriorDesigner.saveDesigner
8. DesignerRegistrationPage.displayMsg
```

### #223 — Set Account Pending Verification

```text
1. VerificationPage.submitDocuments
2. VerificationController.processPending
3. InteriorDesigner.setStatus
4. InteriorDesigner.disablePublish
5. VerificationPage.displayNotice
```

### #224 — Log In

```text
1. LoginPage.navigateToLoginPage
2. LoginPage.displayLoginForm
3. LoginPage.enterCredentials
4. LoginPage.clickLogin
5. LoginController.login
6. UserRepository.validateUser
7. LoginPage.displayMsg
```

### #225 — Log Out

```text
1. NavigationPage.clickLogout
2. LogoutController.logout
3. Session.clearSession
4. NavigationPage.displayMsg
5. NavigationPage.redirectToLogin
```

### #226 — Role-Based Screens

```text
1. AppMenuView.openNavigationMenu
2. AccessController.getAuthorizedScreens
3. UserRole.getPermissions
4. AppMenuView.renderMenu
```

### #227 — Search Accounts

```text
1. AccountManagementPage.navigateToAccountManagement
2. AccountManagementPage.enterSearchKeyword
3. AccountManagementPage.clickSearch
4. AccountSearchController.searchAccounts
5. User.findByKeyword
6. AccountManagementPage.displayAccountList
```

### #228 — Create Account for Any Role

```text
1. CreateAccountPage.navigateToCreateAccount
2. CreateAccountPage.displayForm
3. CreateAccountPage.enterAccountDetails
4. CreateAccountPage.clickCreateAccount
5. CreateAccountController.createAccount
6. User.isEmailRegistered
7. CreateAccountController.hashPassword
8. User.saveUserWithRole
9. CreateAccountPage.displaySuccessMsg
```

### #229 — Suspend or Reactivate Account

```text
1. ManageAccountPage.viewAccountDetails
2. ManageAccountPage.clickUpdateStatus
3. AccountStatusController.updateStatus
4. User.setStatus
5. ManageAccountPage.displaySuccessMsg
6. ManageAccountPage.refreshView
```

### #230 — Delete Account

```text
1. ManageAccountPage.viewAccountDetails
2. ManageAccountPage.clickDelete
3. ManageAccountPage.showConfirmationPrompt
4. ManageAccountPage.confirmDelete
5. DeleteAccountController.deleteUser
6. User.removeAccount
7. ManageAccountPage.displaySuccessMsg
8. ManageAccountPage.refreshView
```

### #256 — Approve or Reject Designer

```text
1. ReviewDesignerPage.viewPendingApplication
2. ReviewDesignerPage.displayApplication
3. ReviewDesignerPage.clickApprove
4. DesignerApprovalController.approveDesigner
5. InteriorDesigner.setStatus
6. InteriorDesigner.enablePublish
7. ReviewDesignerPage.displaySuccessMsg
8. ReviewDesignerPage.refreshQueue
```

### #231 — Edit Company Details

```text
1. CompanyProfilePage.viewCompanyProfile
2. CompanyProfilePage.displayForm
3. CompanyProfilePage.submitDetails
4. CompanyProfileController.saveProfile
5. CompanyProfile.isUenExists
6. CompanyProfile.updateDetails
7. CompanyProfilePage.displaySuccessMsg
8. CompanyProfilePage.refreshPage
```

### #232 — Public Company Profile

```text
1. PublicCompanyProfilePage.viewPublicProfile
2. ViewCompanyProfileController.getPublicProfile
3. CompanyProfile.getDetails
4. PublicCompanyProfilePage.renderProfile
```

### #233 — Create IDP

```text
1. CreateIDPPage.submitIDP
2. CreateIDPController.createIDP
3. IDP.saveIDP
4. CreateIDPPage.displayMessage
```

### #234 — Upload Photos and Choose Cover

```text
1. IDPPhotoPage.submitPhotos
2. UploadPhotosController.uploadPhotos
3. IDPPhoto.savePhotos
4. IDP.setCoverImage
5. IDPPhotoPage.displayMessage
```

### #235 — Edit IDP

```text
1. EditIDPPage.saveChanges
2. EditIDPController.editIDP
3. IDP.updateIDP
4. EditIDPPage.displayMessage
```

### #236 — View Own IDP List

```text
1. MyProjectsPage.clickMyProjects
2. ViewMyIDPsController.getMyIDPs
3. IDP.getIDPsByDesigner
4. MyProjectsPage.displayIDPList
```

### #237 — Search Published IDPs

```text
1. SearchPage.searchIDP
2. SearchIDPController.searchIDPs
3. IDP.findPublishedByKeyword
4. SearchPage.displayResults
```

### #238 — View Search Results

```text
1. SearchResultsPage.submitSearch
2. ViewSearchResultsController.getResults
3. IDP.findPublishedByKeyword
4. SearchResultsPage.displayResults
```

### #239 — View IDP Details and Gallery

```text
1. IDPDetailPage.selectIDP
2. ViewIDPDetailsController.getIDPDetails
3. IDP.getIDP
4. ViewIDPDetailsController.getGallery
5. IDPPhoto.getPhotos
6. IDPDetailPage.displayIDPDetails
7. IDPDetailPage.displayGallery
```

### #240 — Company Link from IDP

```text
1. CompanyProfilePage.clickCompanyName
2. ViewCompanyProfileController.getCompanyProfile
3. Company.getCompanyByIDP
4. CompanyProfilePage.displayCompanyProfile
```

### #242 — Save Favourite without Duplicates

```text
1. IDPDetailPage.saveToFavourites
2. SaveFavouriteController.saveFavourite
3. Favourite.isFavourited
4. Favourite.saveFavourite
5. IDPDetailPage.displayMessage
```

### #263 — Publish or Unpublish IDP

```text
1. MyIDPPage.changePublishStatus
2. PublishIDPController.setPublishStatus
3. IDP.updateStatus
4. MyIDPPage.displayMessage
```

### #266 — Assign IDP Categories

```text
1. IDPCategoryPage.saveCategories
2. AssignCategoriesController.assignCategories
3. IDPCategory.saveCategories
4. IDPCategoryPage.displayMessage
```

### #273 — View Favourites List

```text
1. FavouritesPage.clickMyFavourites
2. ViewFavouritesController.getFavourites
3. Favourite.getFavouriteIDPs
4. FavouritesPage.displayFavourites
```

### #241 — Record IDP View

```text
1. IDPDetailPage.openIDP
2. RecordViewController.recordView
3. IDP.isPublished
4. IDPView.saveView
5. IDPView.getViewCount
6. IDPDetailPage.displayViewCount
```

### #243 — Record IDP Shortlist

```text
1. IDPDetailPage.saveIDP
2. RecordShortlistController.recordShortlist
3. IDPShortlist.isShortlisted
4. IDPShortlist.saveShortlist
5. IDPShortlist.getShortlistCount
6. IDPDetailPage.displayShortlistCount
```

### #244 — Portfolio Totals

```text
1. DesignerDashboardPage.navigateToDashboard
2. ViewTotalStatsController.getTotalStats
3. IDP.countIDPs
4. IDP.getTotalViews
5. IDP.getTotalShortlists
6. DesignerDashboardPage.displayTotalStats
```

### #245 — Stats per IDP

```text
1. DesignerDashboardPage.clickViewByIDP
2. ViewIDPStatsController.getStatsPerIDP
3. IDP.getStatsByDesigner
4. DesignerDashboardPage.displayIDPStatsList
```

### #246 — Create Category

```text
1. CategoryManagementPage.clickCreateCategory
2. CategoryManagementPage.displayCreateForm
3. CategoryManagementPage.submitCategory
4. CreateCategoryController.createCategory
5. Category.nameExists
6. Category.saveCategory
7. CategoryManagementPage.displaySuccessMsg
```

### #247 — Edit Category

```text
1. CategoryManagementPage.navigateToCategories
2. EditCategoryController.getCategories
3. Category.getAllCategories
4. CategoryManagementPage.displayCategoryList
5. CategoryManagementPage.clickEdit
6. CategoryManagementPage.displayEditForm
7. CategoryManagementPage.saveCategory
8. EditCategoryController.updateCategory
9. Category.nameExists
10. Category.updateCategory
11. CategoryManagementPage.displaySuccessMsg
```

### #248 — Make Category Available

```text
1. CategoryManagementPage.navigateToCategories
2. CategoryAvailabilityController.getCategories
3. Category.getAllCategories
4. CategoryManagementPage.displayCategoryList
5. CategoryManagementPage.toggleAvailable
6. CategoryAvailabilityController.setAvailable
7. Category.updateAvailability
8. CategoryManagementPage.displaySuccessMsg
```

### #249 — Select Report Period

```text
1. ReportPage.navigateToReports
2. ReportPage.displayPeriodOptions
3. ReportPage.selectPeriod
4. SelectPeriodController.getPeriod
5. ReportPeriod.setPeriod
6. ReportPeriod.calculateRange
7. ReportPage.displayPeriod
```

### #250 — Generate Activity Totals

```text
1. ReportPage.clickGenerate
2. GenerateReportController.generateTotals
3. ReportPeriod.setPeriod
4. ReportPeriod.calculateRange
5. ActivityReport.countNewUsers
6. ActivityReport.countNewIDPs
7. ActivityReport.countViews
8. ActivityReport.countShortlists
9. ReportPage.displayTotals
```

### #251 — View Daily Report Table

```text
1. ReportPage.clickViewAsTable
2. ViewReportTableController.getReportRows
3. ReportPeriod.setPeriod
4. ReportPeriod.calculateRange
5. ActivityReport.getDailyRows
6. ViewReportTableController.calculateTotalRow
7. ReportPage.displayReportTable
```
