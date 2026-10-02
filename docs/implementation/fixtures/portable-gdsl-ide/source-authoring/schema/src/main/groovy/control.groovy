import gdslacceptance.Environment
assert Environment.Create.With { region 'control' }.region == 'control'
