How to use the styling
```html
<h1 class="page-title">Monitor Device</h1>

<div class="card-surface">
    <label class="form-label" for="devName">Device Name</label>
    <InputText id="devName" class="form-control" @bind-Value="DeviceName"/>

    <button class="btn btn-primary mt-3" @onclick="ConnectAsync">
        <i class="bi bi-bluetooth"></i> Connect
    </button>
</div>
```